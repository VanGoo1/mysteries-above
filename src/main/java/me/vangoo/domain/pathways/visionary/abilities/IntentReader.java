package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class IntentReader extends ActiveAbility {

    private static final int DURATION_SECONDS = 20;
    private static final int RADIUS = 15;
    private static final int COST = 75;
    private static final int COOLDOWN = 30;

    @Override
    public String getName() {
        return "Зчитування намірів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Протягом " + DURATION_SECONDS + "с показує наміри оточуючих кольоровою аурою: " +
                "Червоний - атака, Синій - спостереження, Жовтий - втеча, Білий - спокій.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        context.sendMessageToActionBar(context.getCaster(),
                Component.text("👁 Ви бачите справжні наміри істот (" + DURATION_SECONDS + "с)...")
                        .color(NamedTextColor.AQUA));
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        final Map<UUID, IntentState> lastIntents = new HashMap<>();

        context.scheduleRepeating(new Runnable() {
            int ticksPassed = 0;
            final int maxTicks = DURATION_SECONDS * 20;

            @Override
            public void run() {
                if (ticksPassed >= maxTicks) return;
                ticksPassed += 10;

                List<LivingEntity> nearby = context.getNearbyEntities(RADIUS);

                for (LivingEntity entity : nearby) {
                    if (entity.getUniqueId().equals(context.getCasterId())) continue;

                    IntentState currentIntent = analyzeIntent(entity, context);
                    IntentState previousIntent = lastIntents.getOrDefault(entity.getUniqueId(), IntentState.NEUTRAL);

                    visualizeIntent(context, entity, currentIntent);

                    if (currentIntent != previousIntent) {
                        notifyIntentChange(context, entity, currentIntent, previousIntent);
                    }

                    lastIntents.put(entity.getUniqueId(), currentIntent);
                }

                lastIntents.keySet().removeIf(uuid ->
                        nearby.stream().noneMatch(e -> e.getUniqueId().equals(uuid))
                );
            }
        }, 0, 10);

        return AbilityResult.success();
    }

    private IntentState analyzeIntent(LivingEntity suspect, IAbilityContext context) {
        Player caster = context.getCaster();
        Vector toCaster = caster.getLocation().toVector().subtract(suspect.getLocation().toVector());
        double distance = toCaster.length();
        toCaster.normalize();

        Vector direction = suspect.getEyeLocation().getDirection();
        double dotProduct = direction.dot(toCaster);

        // АГРЕСІЯ
        if (suspect instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.getUniqueId().equals(caster.getUniqueId())) {
                return IntentState.AGGRESSIVE;
            }
        }
        if (suspect instanceof Player p) {
            if (dotProduct > 0.85 && isHoldingWeapon(p)) {
                return IntentState.AGGRESSIVE;
            }
        }

        // ВТЕЧА
        boolean lowHealth = (suspect.getHealth() / suspect.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) < 0.3;
        if (dotProduct < -0.5) {
            if (suspect instanceof Player p && p.isSprinting()) return IntentState.FLEEING;
            if (lowHealth) return IntentState.FLEEING;
        }

        // СПОСТЕРЕЖЕННЯ
        if (dotProduct > 0.7) {
            return IntentState.OBSERVING;
        }

        return IntentState.NEUTRAL;
    }

    private void visualizeIntent(IAbilityContext context, LivingEntity entity, IntentState intent) {
        Location headLoc = entity.getEyeLocation().add(0, 0.5, 0);

        switch (intent) {
            case AGGRESSIVE:
                Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.2f);
                entity.getWorld().spawnParticle(Particle.DUST, headLoc, 8, 0.25, 0.25, 0.25, 0, redDust);
                break;

            case OBSERVING:
                Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(50, 120, 255), 1.0f);
                entity.getWorld().spawnParticle(Particle.DUST, headLoc, 6, 0.2, 0.2, 0.2, 0, blueDust);
                break;

            case FLEEING:
                Particle.DustOptions yellowDust = new Particle.DustOptions(Color.fromRGB(255, 220, 50), 1.0f);
                entity.getWorld().spawnParticle(Particle.DUST, headLoc, 6, 0.25, 0.25, 0.25, 0, yellowDust);
                break;

            case NEUTRAL:
                Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(240, 240, 240), 0.8f);
                entity.getWorld().spawnParticle(Particle.DUST, headLoc, 4, 0.15, 0.15, 0.15, 0, whiteDust);
                break;
        }
    }

    private void notifyIntentChange(IAbilityContext context, LivingEntity entity,
                                    IntentState current, IntentState previous) {
        String entityName = entity instanceof Player ? entity.getName() : entity.getType().name();

        if (current == IntentState.AGGRESSIVE) {
            context.sendMessageToActionBar(context.getCaster(),
                    Component.text("⚠ " + entityName + " готується до атаки!").color(NamedTextColor.RED));
            context.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        } else if (current == IntentState.FLEEING && previous == IntentState.AGGRESSIVE) {
            context.sendMessageToActionBar(context.getCaster(),
                    Component.text("⬇ " + entityName + " відступає.").color(NamedTextColor.YELLOW));
        } else if (current == IntentState.OBSERVING) {
            context.sendMessageToActionBar(context.getCaster(),
                    Component.text("👀 " + entityName + " спостерігає.").color(NamedTextColor.BLUE));
        }
    }

    private boolean isHoldingWeapon(Player p) {
        String type = p.getInventory().getItemInMainHand().getType().name();
        return type.contains("SWORD") || type.contains("AXE") ||
                type.contains("BOW") || type.contains("TRIDENT");
    }

    private enum IntentState {
        AGGRESSIVE,
        OBSERVING,
        FLEEING,
        NEUTRAL
    }
}
