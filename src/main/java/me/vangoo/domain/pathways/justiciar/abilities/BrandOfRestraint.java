package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;

/**
 * Sequence 8 Interrogator: Brand of Restraint
 *
 * Накладає ілюзорне клеймо, яке паралізує тіло і блокує дух.
 */
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
                "Ціль " + ChatColor.RED + "знерухомлюється" + ChatColor.RESET + ", " +
                "її атаки " + ChatColor.GRAY + "сповільнюються" + ChatColor.RESET + ", " +
                "а здібності " + ChatColor.DARK_PURPLE + "блокуються" + ChatColor.RESET + " на " + DURATION_SECONDS + " сек.";
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
        return context.getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();

        // 1. Шукаємо ціль (повторно, бо це етап виконання)
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Ви повинні дивитися на ціль, щоб накласти клеймо.");
        }
        LivingEntity target = targetOpt.get();

        // 2. ВІЗУАЛІЗАЦІЯ КЛЕЙМА
        context.playCircleEffect(target.getEyeLocation().add(0, 0.5, 0), 0.8, Particle.FLAME, 20);
        context.playHelixEffect(target.getLocation(), target.getEyeLocation().add(0, 1, 0), Particle.CRIT, 20);
        context.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 2.0f);
        context.playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);

        // 3. ЕФЕКТИ КОНТРОЛЮ

        // Фізичне обмеження (працює на всіх)
        context.applyEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, DURATION_TICKS, 5);
        context.applyEffect(target.getUniqueId(), PotionEffectType.JUMP_BOOST, DURATION_TICKS, 200);
        context.applyEffect(target.getUniqueId(), PotionEffectType.MINING_FATIGUE, DURATION_TICKS, 2);
        context.applyEffect(target.getUniqueId(), PotionEffectType.WEAKNESS, DURATION_TICKS, 1);

        // Візуальна мітка (тепер працює і на мобах, і на гравцях)
        context.setGlowing(target.getUniqueId(), ChatColor.GOLD, DURATION_TICKS);

        // Логіка для гравців (блокування магії та чат)
        if (target instanceof Player) {
            context.lockAbilities(target.getUniqueId(), DURATION_SECONDS);
            context.sendMessage(target.getUniqueId(), ChatColor.RED + "На вас накладено Клеймо Обмеження! Ви придушені.");
        }

        context.sendMessageToCaster(ChatColor.GOLD + "Ви обмежили рухи та волю " + target.getName());

        return AbilityResult.success();
    }
}