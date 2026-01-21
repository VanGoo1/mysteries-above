package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

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
                "Ціль §cзнерухомлюється§r, " +
                "її атаки §7сповільнюються§r, " +
                "а здібності §5блокуються§r на " + DURATION_SECONDS + " сек.";
    }

    @Override
    public int getSpiritualityCost() {
        return 100;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 25;
    }

    /**
     * Вмикає перевірку Sequence Suppression.
     * Якщо ціль має значно вищий рівень (наприклад, ви Seq 8, а ворог Seq 5),
     * здібність автоматично провалиться або буде заблокована перед виконанням.
     */
    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // 1. Шукаємо ціль (повторно, бо це етап виконання)
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Ви повинні дивитися на ціль, щоб накласти клеймо.");
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        // 2. ВІЗУАЛІЗАЦІЯ КЛЕЙМА
        showBrandingEffect(context, target);

        // 3. ЕФЕКТИ КОНТРОЛЮ
        applyRestraintEffects(context, targetId);

        // 4. ВІЗУАЛЬНА МІТКА
        context.glowing().setGlowing(targetId, casterId, ChatColor.GOLD, DURATION_TICKS);

        // 5. ЛОГІКА ДЛЯ ГРАВЦІВ (блокування магії)
        if (target instanceof Player) {
            context.cooldown().lockAbilities(targetId, DURATION_SECONDS);
            context.messaging().sendMessage(
                    targetId,
                    "§cНа вас накладено Клеймо Обмеження! Ви придушені."
            );
        }

        // Отримуємо ім'я через Data Context
        String targetName = context.playerData().getName(targetId);
        context.messaging().sendMessage(
                casterId,
                "§6Ви обмежили рухи та волю " + targetName
        );

        return AbilityResult.success();
    }

    /**
     * Показує візуальний ефект накладання клейма
     */
    private void showBrandingEffect(IAbilityContext context, LivingEntity target) {
        // Коло навколо голови
        context.effects().playCircleEffect(
                target.getEyeLocation().add(0, 0.5, 0),
                0.8,
                Particle.FLAME,
                20
        );

        // Спіраль від ніг до голови
        context.effects().playHelixEffect(
                target.getLocation(),
                target.getEyeLocation().add(0, 1, 0),
                Particle.CRIT,
                20
        );

        // Звуки
        context.effects().playSound(
                target.getLocation(),
                Sound.BLOCK_ANVIL_LAND,
                0.8f,
                2.0f
        );

        context.effects().playSound(
                target.getLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH,
                1.0f,
                1.0f
        );

        // Додатковий ефект "halo" над головою
        context.effects().playAlertHalo(
                target.getEyeLocation().add(0, 0.8, 0),
                Color.ORANGE
        );
    }

    /**
     * Накладає ефекти обмеження на ціль
     */
    private void applyRestraintEffects(IAbilityContext context, UUID targetId) {
        // Фізичне обмеження (працює на всіх)
        context.entity().applyPotionEffect(
                targetId,
                PotionEffectType.SLOWNESS,
                DURATION_TICKS,
                5
        );

        context.entity().applyPotionEffect(
                targetId,
                PotionEffectType.JUMP_BOOST,
                DURATION_TICKS,
                200 // Від'ємний jump boost = неможливість стрибати
        );

        context.entity().applyPotionEffect(
                targetId,
                PotionEffectType.MINING_FATIGUE,
                DURATION_TICKS,
                2
        );

        context.entity().applyPotionEffect(
                targetId,
                PotionEffectType.WEAKNESS,
                DURATION_TICKS,
                1
        );
    }
}