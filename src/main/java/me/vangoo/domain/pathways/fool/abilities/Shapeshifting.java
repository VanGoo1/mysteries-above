package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 6: Faceless — Shapeshifting (Перевтілення)
 *
 * The Faceless can alter their appearance to mimic another player.
 * Implemented via invisibility + hologram with target's name.
 * Breaks on attack or damage taken.
 */
public class Shapeshifting extends ActiveAbility {

    private static final int BASE_COST = 150;
    private static final int BASE_COOLDOWN = 120;
    private static final int DURATION_TICKS = 1200; // 60 seconds
    private static final double TARGET_RANGE = 5.0;

    private static final Map<UUID, ShapeshiftData> activeShapeshifts = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Перевтілення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Приймаєте вигляд іншого гравця на " + (DURATION_TICKS / 20) + "с. " +
                "Агресія або отримання шкоди знімає маскування.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    /**
     * Check if a player is currently shapeshifted.
     */
    public static boolean isShapeshifted(UUID playerId) {
        return activeShapeshifts.containsKey(playerId);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // If already shapeshifted, cancel
        if (activeShapeshifts.containsKey(casterId)) {
            cancelShapeshift(context, casterId, "свідоме скасування");
            return AbilityResult.success();
        }

        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(TARGET_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Потрібно дивитися на гравця (макс " + (int) TARGET_RANGE + " блоків)");
        }

        Player target = targetOpt.get();
        String targetName = target.getName();

        activateShapeshift(context, casterId, targetName);
        return AbilityResult.success();
    }

    private void activateShapeshift(IAbilityContext context, UUID casterId, String disguiseName) {
        // Transformation animation
        Location loc = context.playerData().getCurrentLocation(casterId);
        if (loc != null) {
            context.effects().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4);
            context.effects().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            context.effects().playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.0f);
        }

        // Hide player from everyone
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(casterId)) {
                context.entity().hidePlayerFromTarget(other.getUniqueId(), casterId);
            }
        }

        // Apply invisibility
        context.entity().applyPotionEffect(casterId, org.bukkit.potion.PotionEffectType.INVISIBILITY,
                DURATION_TICKS + 20, 0);

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("🎭 Ви маскуєтесь під " + disguiseName, NamedTextColor.GOLD));

        final int[] ticks = {0};
        final boolean[] broken = {false};

        // Subscribe to aggression events (breaks disguise)
        context.events().subscribeToTemporaryEvent(casterId,
                EntityDamageByEntityEvent.class,
                e -> e.getDamager().getUniqueId().equals(casterId)
                        || e.getEntity().getUniqueId().equals(casterId),
                e -> {
                    if (!broken[0]) {
                        broken[0] = true;
                        cancelShapeshift(context, casterId, "бойова дія");
                    }
                },
                DURATION_TICKS
        );

        // Main loop
        BukkitTask task = context.scheduling().scheduleRepeating(() -> {
            ticks[0]++;

            if (!context.playerData().isOnline(casterId) || broken[0]) {
                cancelShapeshift(context, casterId, "вихід");
                return;
            }

            if (ticks[0] >= DURATION_TICKS) {
                cancelShapeshift(context, casterId, "час вийшов");
                return;
            }

            // Refresh hologram every 3 seconds
            if (ticks[0] % 60 == 0) {
                Location currentLoc = context.playerData().getCurrentLocation(casterId);
                if (currentLoc != null) {
                    context.messaging().spawnTemporaryHologram(
                            currentLoc.clone().add(0, 2.3, 0),
                            Component.text(disguiseName, NamedTextColor.WHITE),
                            60L
                    );
                }
            }

            // Refresh hide from new players every 2 seconds
            if (ticks[0] % 40 == 0) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(casterId)) {
                        context.entity().hidePlayerFromTarget(other.getUniqueId(), casterId);
                    }
                }
            }

            // Action bar update every 4 seconds
            if (ticks[0] % 80 == 0) {
                int remaining = (DURATION_TICKS - ticks[0]) / 20;
                context.messaging().sendMessageToActionBar(casterId,
                        Component.text("🎭 " + disguiseName + " (" + remaining + "с)", NamedTextColor.GOLD));
            }
        }, 0L, 1L);

        activeShapeshifts.put(casterId, new ShapeshiftData(task, disguiseName));
    }

    private void cancelShapeshift(IAbilityContext context, UUID casterId, String reason) {
        ShapeshiftData data = activeShapeshifts.remove(casterId);
        if (data == null) return;

        data.task.cancel();
        context.events().unsubscribeAll(casterId);

        // Show player to everyone
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(casterId)) {
                context.entity().showPlayerToTarget(other.getUniqueId(), casterId);
            }
        }

        // Remove invisibility
        context.entity().removePotionEffect(casterId, org.bukkit.potion.PotionEffectType.INVISIBILITY);

        // Reversal animation
        Location loc = context.playerData().getCurrentLocation(casterId);
        if (loc != null) {
            context.effects().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            context.effects().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
        }

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("🎭 Перевтілення знято • " + reason, NamedTextColor.GRAY));

        context.cooldown().setCooldown(this, casterId);
    }

    private static class ShapeshiftData {
        final BukkitTask task;
        final String disguiseName;

        ShapeshiftData(BukkitTask task, String disguiseName) {
            this.task = task;
            this.disguiseName = disguiseName;
        }
    }

    @Override
    public void cleanUp() {
        for (ShapeshiftData data : activeShapeshifts.values()) {
            data.task.cancel();
        }
        activeShapeshifts.clear();
    }
}
