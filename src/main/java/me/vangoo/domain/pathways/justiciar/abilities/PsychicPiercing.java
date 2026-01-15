package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

public class PsychicPiercing extends ActiveAbility {

    private static final double MAX_RANGE = 5.0; // Як в описі - 5 метрів
    private static final double SPIRIT_DAMAGE = 12.0; // 6 сердець чистого урону
    private static final int PREPARATION_TIME_TICKS = 100; // 5 секунд на пошук цілі

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
        Player caster = context.getCasterPlayer();

        // Візуал підготовки: очі починають світитися
        context.sendMessageToCaster(ChatColor.AQUA + "👁 Ви підготували Психічний Прокол. Подивіться на жертву...");
        context.playSoundToCaster(Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);

        // Запускаємо таймер, який чекає на жертву
        new MentalStrikeTask(context, caster).start();

        return AbilityResult.success();
    }

    /**
     * Внутрішній клас завдання, яке сканує погляд гравця
     */
    private class MentalStrikeTask {
        private final IAbilityContext context;
        private final Player caster;
        private final BukkitTask task;
        private int ticksRun = 0;

        public MentalStrikeTask(IAbilityContext context, Player caster) {
            this.context = context;
            this.caster = caster;
            // Запускаємо повторювану задачу кожні 2 тіки (0.1 сек) для швидкої реакції
            this.task = context.scheduleRepeating(this::tick, 0, 2);
        }

        public void start() {
            // Логіка вже в конструкторі, але можна додати стартові ефекти
        }

        private void tick() {
            ticksRun += 2;

            // 1. Перевірка часу дії (якщо нікого не знайшли за 5 сек - скасовуємо)
            if (ticksRun >= PREPARATION_TIME_TICKS || !caster.isOnline()) {
                cancel(false);
                return;
            }

            // 2. Візуалізація "Заряджених очей" (частинки біля очей кастера)
            if (ticksRun % 10 == 0) {
                Location eyeLoc = caster.getEyeLocation();
                context.spawnParticle(Particle.ELECTRIC_SPARK, eyeLoc, 2, 0.2, 0.1, 0.2);
            }

            // 3. Пошук цілі поглядом (RayTrace)
            RayTraceResult result = caster.getWorld().rayTraceEntities(
                    caster.getEyeLocation(),
                    caster.getEyeLocation().getDirection(),
                    MAX_RANGE,
                    0.5, // Розмір променя (трохи "товстіший", щоб легше влучити)
                    entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(caster.getUniqueId())
            );

            if (result != null && result.getHitEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) result.getHitEntity();
                triggerPiercing(target);
                cancel(true); // Успішно спрацювало
            }
        }

        private void triggerPiercing(LivingEntity target) {
            // === ВІЗУАЛ ===
            // Блискавка з очей (Beam effect)
            context.playBeamEffect(caster.getEyeLocation(), target.getEyeLocation(), Particle.FIREWORK, 0.1, 5);
            context.playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.5f); // Пронизливий звук
            context.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2.0f);

            // === ЕФЕКТИ (ПРОБИТТЯ ЗАХИСТУ) ===

            // 1. Зняття бафів (Invade mental defenses)
            // Видаляємо Resistance, Regen, FireRes, Absorption
            context.removeEffect(target.getUniqueId(), PotionEffectType.RESISTANCE);
            context.removeEffect(target.getUniqueId(), PotionEffectType.REGENERATION);
            context.removeEffect(target.getUniqueId(), PotionEffectType.ABSORPTION);

            // 2. Стан вразливості (Subject to counterattack)
            // Зупиняємо (Slowness) і забороняємо стрибати (Jump Boost negative)
            context.applyEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, 20, 10); // 1 секунда повного стопу
            context.applyEffect(target.getUniqueId(), PotionEffectType.JUMP_BOOST, 20, 128); // Заборона стрибка

            // 3. Урон Духовному Тілу (Direct Damage)
            // Використовуємо damage(), але оскільки це "Spirit Body", можна нанести
            // трохи Wither ефекту для візуалізації болю
            context.damage(target.getUniqueId(), SPIRIT_DAMAGE);
            context.applyEffect(target.getUniqueId(), PotionEffectType.WITHER, 40, 1);

            // Повідомлення
            context.sendMessageToCaster(ChatColor.GOLD + "⚡ Психічний прокол успішний!");
            if (target instanceof Player) {
                context.sendMessage(target.getUniqueId(), ChatColor.RED + "Ваш розум пронизав нестерпний біль!");
                // Ефект тряски камери (легка нудота на 2 сек)
                context.applyEffect(target.getUniqueId(), PotionEffectType.NAUSEA, 50, 0);
            }
        }

        private void cancel(boolean success) {
            task.cancel();
            if (!success) {
                context.sendMessageToCaster(ChatColor.GRAY + "Концентрація розсіялась...");
            }
        }
    }
}