package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.UUID;

public class BrandOfRestraint extends ActiveAbility {

    private static final double RANGE = 15.0;
    private static final int DURATION_SECONDS = 6;
    private static final int DURATION_TICKS = DURATION_SECONDS * 20;

    @Override
    public String getName() {
        return "Клеймо Обмеження";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Випалює ілюзорне клеймо на душі ворога.\n" +
                "Ціль §cповністю знерухомлюється§r, " +
                "а її здібності §5блокуються§r на " + DURATION_SECONDS + " сек.";
    }

    @Override
    public int getSpiritualityCost() {
        return 100;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 25;
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Ви повинні дивитися на ціль, щоб накласти клеймо.");
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        // 2. ВІЗУАЛІЗАЦІЯ КЛЕЙМА (початковий вибух ефектів)
        showBrandingEffect(context, target);

        // 3. ЕФЕКТИ ПОВНОГО ЗНЕРУХОМЛЕННЯ (Якір)
        applyCompleteImmobilization(context, target);

        // 4. ВІЗУАЛЬНА МІТКА (світіння)
        context.glowing().setGlowing(targetId, casterId, ChatColor.GOLD, DURATION_TICKS);

        // 5. ЛОГІКА ДЛЯ ГРАВЦІВ (блокування магії)
        if (target instanceof Player) {
            context.cooldown().lockAbilities(targetId, DURATION_SECONDS);

            context.messaging().sendMessageToActionBar(
                    targetId,
                    Component.text("Вас скуто Клеймом Обмеження! Рух неможливий.", NamedTextColor.RED)
            );
        }

        String targetName = context.playerData().getName(targetId);
        context.messaging().sendMessage(
                casterId,
                "§6Ви повністю обмежили рухи та волю " + targetName
        );

        return AbilityResult.success();
    }

    private void showBrandingEffect(IAbilityContext context, LivingEntity target) {
        context.effects().playCircleEffect(
                target.getEyeLocation().add(0, 0.5, 0),
                0.8,
                Particle.FLAME,
                20
        );
        context.effects().playHelixEffect(
                target.getLocation(),
                target.getEyeLocation().add(0, 1, 0),
                Particle.CRIT,
                20
        );
        context.effects().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 2.0f);
        context.effects().playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
        context.effects().playAlertHalo(target.getEyeLocation().add(0, 0.8, 0), Color.ORANGE);
    }

    /**
     * Реалізація "Якоря" та жорстких ефектів контролю.
     */
    private void applyCompleteImmobilization(IAbilityContext context, LivingEntity target) {
        UUID targetId = target.getUniqueId();

        // 1. Накладаємо "важкі" ефекти
        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, DURATION_TICKS, 255);
        context.entity().applyPotionEffect(targetId, PotionEffectType.JUMP_BOOST, DURATION_TICKS, 250);
        context.entity().applyPotionEffect(targetId, PotionEffectType.MINING_FATIGUE, DURATION_TICKS, 255);
        context.entity().applyPotionEffect(targetId, PotionEffectType.WEAKNESS, DURATION_TICKS, 5);

        // 2. Механіка "Якоря"
        final Location anchorLocation = target.getLocation().clone();

        // Трохи піднімаємо точку, якщо блок знизу твердий
        if (anchorLocation.getBlock().getType().isSolid()) {
            anchorLocation.add(0, 0.1, 0);
        }

        // Змінні для керування завданням
        final int[] elapsed = {0};
        final org.bukkit.scheduler.BukkitTask[] taskRef = new org.bukkit.scheduler.BukkitTask[1];

        // Запускаємо через ваш контекст
        taskRef[0] = context.scheduling().scheduleRepeating(() -> {

            // А. Перевірка часу дії: Зупиняємо таск, якщо час вийшов
            if (elapsed[0] >= DURATION_TICKS) {
                if (taskRef[0] != null) taskRef[0].cancel();
                return;
            }

            // Б. Перевірка валідності цілі (щоб не вилітало помилок)
            LivingEntity victim = (LivingEntity) Bukkit.getEntity(targetId);
            if (victim == null || !victim.isValid() || victim.isDead()) {
                if (taskRef[0] != null) taskRef[0].cancel(); // Зупиняємо, якщо ціль зникла
                return;
            }

            // В. Логіка утримання
            Location current = victim.getLocation();

            // Дистанція > 0.2 блоку (0.04 squared)
            if (current.distanceSquared(anchorLocation) > 0.04) {

                Location pullBack = anchorLocation.clone();
                // Зберігаємо кут огляду (Yaw/Pitch)
                pullBack.setYaw(current.getYaw());
                pullBack.setPitch(current.getPitch());

                // Гасимо інерцію (через контекст або напряму, якщо в контексті немає setVelocity)
                victim.setVelocity(new Vector(0, 0, 0));

                // Телепортуємо назад
                victim.teleport(pullBack);
            }

            // Г. Візуалізація (кожні 0.5 сек)
            if (elapsed[0] % 10 == 0) {
                context.effects().spawnParticle(Particle.CRIT, anchorLocation.clone().add(0, 0.5, 0), 5, 0.3, 0.5, 0.3);
                context.effects().playSound(anchorLocation, Sound.BLOCK_CHAIN_BREAK, 0.5f, 0.5f);
            }

            elapsed[0] += 2; // +2 тіка
        }, 0, 2);
    }
}