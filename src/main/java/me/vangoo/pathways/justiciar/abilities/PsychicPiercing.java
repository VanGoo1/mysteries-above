package me.vangoo.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.UUID;

public class PsychicPiercing extends ActiveAbility {

    private static final double MAX_RANGE = 5.0;
    private static final double SPIRIT_DAMAGE = 12.0;
    private static final int PREPARATION_TIME_TICKS = 100; // 5 секунд

    @Override
    public String getName() {
        return "Психічний Прокол";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви концентруєте ментальну енергію в очах. " +
                "Наступний погляд на ворога (до 5м) випустить блискавку, що проб'є Духовне Тіло, " +
                "знищить магічний захист і паралізує ціль.";
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 20;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Візуал підготовки
        context.messaging().sendMessage(casterId, ChatColor.AQUA + "👁 Ви підготували Психічний Прокол. Подивіться на жертву...");
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);

        // Запускаємо таймер сканування
        new MentalStrikeTask(context, casterId).start();

        return AbilityResult.success();
    }

    /**
     * Внутрішній клас для сканування погляду
     */
    private static class MentalStrikeTask {
        private final IAbilityContext context;
        private final UUID casterId;
        private int ticksRun = 0;
        private BukkitTask task; // Field to hold the BukkitTask

        public MentalStrikeTask(IAbilityContext context, UUID casterId) {
            this.context = context;
            this.casterId = casterId;
        }

        public void start() {
            // Запускаємо повторювану задачу
            this.task = context.scheduling().scheduleRepeating(this::tick, 0, 2); // Store the task
        }

        private void tick() {
            ticksRun += 2;

            // Перевірка таймауту
            if (ticksRun >= PREPARATION_TIME_TICKS || !context.playerData().isOnline(casterId)) {
                cancel(false);
                return;
            }

            // Візуалізація "заряджених очей"
            if (ticksRun % 10 == 0) {
                Location eyeLoc = context.playerData().getEyeLocation(casterId);
                if (eyeLoc != null) {
                    context.effects().spawnParticle(Particle.ELECTRIC_SPARK, eyeLoc, 2, 0.2, 0.1, 0.2);
                }
            }

            // Пошук цілі через RayTrace
            var targetOpt = context.targeting().getTargetedEntity(MAX_RANGE);

            if (targetOpt.isPresent()) {
                LivingEntity target = targetOpt.get();
                triggerPiercing(target.getUniqueId());
                cancel(true);
            }
        }

        private void triggerPiercing(UUID targetId) {
            Location casterEye = context.playerData().getEyeLocation(casterId);
            Location targetEye = context.playerData().getEyeLocation(targetId);

            if (casterEye == null || targetEye == null) {
                cancel(false); // Cancel if locations are null to prevent infinite running.
                return;
            }

            // === ВІЗУАЛЬНІ ЕФЕКТИ ===

            // Блискавка з очей (beam effect)
            context.effects().playBeamEffect(casterEye, targetEye, Particle.FIREWORK, 0.1, 5);

            // Звуки
            Location casterLoc = context.playerData().getCurrentLocation(casterId);
            Location targetLoc = context.playerData().getCurrentLocation(targetId);

            if (casterLoc != null) {
                context.effects().playSound(casterLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.5f);
            }
            if (targetLoc != null) {
                context.effects().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2.0f);
            }

            // === ЗНЯТТЯ ЗАХИСНИХ БАФІВ ===
            context.entity().removePotionEffect(targetId, PotionEffectType.RESISTANCE);
            context.entity().removePotionEffect(targetId, PotionEffectType.REGENERATION);
            context.entity().removePotionEffect(targetId, PotionEffectType.ABSORPTION);

            // === ПАРАЛІЗАЦІЯ ===
            // Повна зупинка (Slowness 10)
            context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, 20, 10);
            // Заборона стрибка
            context.entity().applyPotionEffect(targetId, PotionEffectType.JUMP_BOOST, 20, 128);

            // === УРОН ДУХОВНОМУ ТІЛУ ===
            context.entity().damage(targetId, SPIRIT_DAMAGE);
            // Візуалізація болю (Wither effect)
            context.entity().applyPotionEffect(targetId, PotionEffectType.WITHER, 40, 1);

            // === ПОВІДОМЛЕННЯ ===
            context.messaging().sendMessage(casterId, ChatColor.GOLD + "⚡ Психічний прокол успішний!");

            if (context.playerData().isOnline(targetId)) {
                context.messaging().sendMessage(targetId, ChatColor.RED + "Ваш розум пронизав нестерпний біль!");
                // Ефект тряски камери (нудота)
                context.entity().applyPotionEffect(targetId, PotionEffectType.NAUSEA, 50, 0);
            }

            // Візуальний ефект алерту над ціллю
            if (targetLoc != null) {
                context.effects().playAlertHalo(targetLoc.clone().add(0, 2.2, 0), Color.RED);
            }
        }

        private void cancel(boolean success) {
            if (this.task != null && !this.task.isCancelled()) {
                this.task.cancel(); // Actually cancel the task
            }
            if (!success) {
                context.messaging().sendMessage(casterId, ChatColor.GRAY + "Концентрація розсіялась...");
            }
        }
    }
}