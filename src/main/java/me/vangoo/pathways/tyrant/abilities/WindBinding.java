package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * Sequence 5: Вітряні Пута. Океанський Співець закручує довкола цілі спіральний вихор,
 * і той приковує її до місця: ноги не несуть, стрибок не виходить, а вітер щомиті
 * гасить будь-який рух.
 */
public class WindBinding extends ActiveAbility {

    private static final int BASE_DURATION_TICKS = 100;  // 5 с
    private static final double MAX_RANGE = 20.0;
    private static final int SLOWNESS_AMPLIFIER = 5;     // SLOWNESS VI
    private static final int JUMP_BLOCK_AMPLIFIER = 128; // від'ємний стрибок — ціль не відривається від землі
    private static final int COOLDOWN = 30;
    private static final int SPIRITUALITY_COST = 150;

    @Override
    public String getName() {
        return "Вітряні Пута";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int duration = scaleValue(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fСпіральний вихор обвиває ціль на лінії зору §7(до §b%.0f §7блоків)§f і " +
                        "приковує її до місця на §b%.1f с§f: ані кроку, ані стрибка.",
                MAX_RANGE, duration / 20.0);
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }

        Optional<LivingEntity> targeted = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targeted.isEmpty()) {
            return AbilityResult.failure("Немає цілі на лінії зору");
        }
        LivingEntity target = targeted.get();

        int duration = scaleValue(BASE_DURATION_TICKS, context.getCasterBeyonder().getSequence(),
                SequenceScaler.ScalingStrategy.MODERATE);
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());

        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.SLOWNESS,
                duration, SLOWNESS_AMPLIFIER);
        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.JUMP_BOOST,
                duration, JUMP_BLOCK_AMPLIFIER);

        Location base = target.getLocation();
        context.effects().playRisingSpiral(base, 2.6, 1.1, color, duration);
        context.effects().playVortexEffect(base, 2.4, 1.4, Particle.SMALL_GUST, duration);
        context.effects().playSound(base, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 0.7f);
        context.effects().playSound(base, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.9f, 0.6f);

        // Вітер щотіка гасить рух — без цього ціль повзе навіть під SLOWNESS VI.
        int[] tick = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            tick[0]++;
            if (tick[0] > duration || !target.isValid() || target.isDead()) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            target.setVelocity(new Vector(0, target.isOnGround() ? 0 : -0.1, 0));
        }, 1L, 1L);

        return AbilityResult.success();
    }
}
