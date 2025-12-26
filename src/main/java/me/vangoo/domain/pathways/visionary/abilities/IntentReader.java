package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class IntentReader extends ActiveAbility {

    private static final int DURATION_SECONDS = 20;
    private static final int RADIUS = 15;
    private static final int SPIRITUALITY_COST = 70;
    private static final int COOLDOWN = 30;

    // Enum для визначення стану
    private enum IntentType {
        AGGRESSIVE(ChatColor.RED, Particle.CRIMSON_SPORE),         // Червоний
        OBSERVING(ChatColor.BLUE, Particle.SOUL_FIRE_FLAME),  // Синій
        FLEEING(ChatColor.YELLOW, Particle.WAX_ON),           // Жовтий
        NEUTRAL(ChatColor.WHITE, Particle.END_ROD);           // Білий

        final ChatColor chatColor;
        final Particle particle;

        IntentType(ChatColor chatColor, Particle particle) {
            this.chatColor = chatColor;
            this.particle = particle;
        }
    }

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
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        context.sendMessageToCaster(ChatColor.AQUA + "👁 Ви бачите справжні наміри істот (" + DURATION_SECONDS + "с)...");
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        // Зберігаємо попередній стан, щоб писати в чат тільки про зміни
        final Map<UUID, IntentType> lastIntents = new HashMap<>();

        // Запускаємо цикл перевірки (кожні 0.5 сек / 10 тіків)
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

                    // 1. Аналіз наміру
                    IntentType currentIntent = analyzeIntent(entity, context.getCaster());
                    IntentType previousIntent = lastIntents.getOrDefault(entity.getUniqueId(), IntentType.NEUTRAL);

                    // 2. Візуалізація (Партикли)
                    playIntentParticles(context, entity, currentIntent);

                    // 3. Сповіщення в чат при зміні на критичний стан
                    if (currentIntent != previousIntent) {
                        if (currentIntent == IntentType.AGGRESSIVE) {
                            context.sendMessageToCaster(ChatColor.RED + "⚠ " + entity.getName() + " готується до атаки!");
                            context.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                        } else if (currentIntent == IntentType.FLEEING && previousIntent == IntentType.AGGRESSIVE) {
                            context.sendMessageToCaster(ChatColor.YELLOW + "⬇ " + entity.getName() + " відступає.");
                        }
                    }

                    lastIntents.put(entity.getUniqueId(), currentIntent);
                }

                // Очищення кешу для тих, хто зник
                lastIntents.keySet().removeIf(uuid -> nearby.stream().noneMatch(e -> e.getUniqueId().equals(uuid)));
            }
        }, 0, 10);

        return AbilityResult.success();
    }

    // --- ЛОГІКА ВИЗНАЧЕННЯ НАМІРІВ ---

    private IntentType analyzeIntent(LivingEntity suspect, Player caster) {
        // Вектор від цілі до кастера
        Vector toCaster = caster.getLocation().toVector().subtract(suspect.getLocation().toVector());
        double distance = toCaster.length();
        toCaster.normalize();

        // Куди дивиться ціль
        Vector direction = suspect.getEyeLocation().getDirection();
        double dotProduct = direction.dot(toCaster);
        // dotProduct: 1.0 = дивиться прямо на кастера, -1.0 = дивиться спиною до кастера

        // --- 1. ПЕРЕВІРКА НА АГРЕСІЮ (ЧЕРВОНИЙ) ---
        if (suspect instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.getUniqueId().equals(caster.getUniqueId())) {
                return IntentType.AGGRESSIVE;
            }
        }
        if (suspect instanceof Player p) {
            // Якщо гравець дивиться на вас (кут < 30 град) І тримає зброю
            if (dotProduct > 0.85 && isHoldingWeapon(p)) {
                return IntentType.AGGRESSIVE;
            }
        }

        // --- 2. ПЕРЕВІРКА НА ВТЕЧУ (ЖОВТИЙ) ---
        // Якщо дивиться в протилежний бік (кут > 90 град) І (спринтує АБО здоров'я мало)
        boolean lowHealth = (suspect.getHealth() / suspect.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) < 0.3;
        if (dotProduct < -0.5) {
            if (suspect instanceof Player p && p.isSprinting()) return IntentType.FLEEING;
            if (lowHealth) return IntentType.FLEEING;
        }

        // --- 3. ПЕРЕВІРКА НА СПОСТЕРЕЖЕННЯ (СИНІЙ) ---
        // Якщо просто дивиться на вас, але не атакує і не тікає
        if (dotProduct > 0.7) {
            return IntentType.OBSERVING;
        }

        // --- 4. НЕЙТРАЛЬНИЙ (БІЛИЙ) ---
        return IntentType.NEUTRAL;
    }

    private void playIntentParticles(IAbilityContext context, LivingEntity target, IntentType intent) {
        Location headLoc = target.getEyeLocation().add(0, 0.5, 0);

        // Використовуємо різні типи партиклів для різних кольорів,
        // оскільки IAbilityContext може не підтримувати RGB DustOptions напряму.

        context.spawnParticle(
                intent.particle,
                headLoc,
                5,      // Кількість
                0.15,   // Кучність X
                0.15,   // Кучність Y
                0.15    // Кучність Z
        );
    }

    private boolean isHoldingWeapon(Player p) {
        String type = p.getInventory().getItemInMainHand().getType().name();
        return type.contains("SWORD") || type.contains("AXE") || type.contains("BOW") || type.contains("TRIDENT");
    }
}