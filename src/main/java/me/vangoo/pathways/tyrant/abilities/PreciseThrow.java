package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Sequence 7: Точний Кидок. Навігаційне чуття Мореплавця робить кидок безпомилковим —
 * водяний болт летить у ціль, у яку дивиться гравець, і не промахується. Скромна за силою,
 * але гарантований влучанням активний вияв майстерності орієнтації.
 */
public class PreciseThrow extends ActiveAbility {

    private static final int BASE_DAMAGE = 8;
    private static final double MAX_RANGE = 30.0;
    private static final double KNOCKBACK = 0.5;
    private static final int COOLDOWN = 6;
    private static final int SPIRITUALITY_COST = 50;

    @Override
    public String getName() {
        return "Точний Кидок";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fЧуття орієнтації робить кидок безпомилковим: водяний болт летить у ціль, " +
                        "у яку ви дивитесь, і не промахується — §c%d §fшкоди.", damage);
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

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        Location start = player.getEyeLocation();
        Location end = target.getLocation().add(0, target.getHeight() * 0.5, 0);

        // Водяний болт-промінь до цілі + гарантоване влучання при прибутті.
        var color = PathwayBranding.liquidOf("Tyrant");
        context.effects().playBeamEffect(start, end, Particle.BUBBLE, 0.4, 6);
        context.effects().playTravelingBeam(start, end, color, () -> {
            if (!target.isValid()) return;
            target.damage(damage, player);
            target.setVelocity(target.getVelocity().add(
                    end.toVector().subtract(start.toVector()).normalize().multiply(KNOCKBACK)));
            context.effects().playWaveEffect(target.getLocation(), 1.5, Particle.SPLASH, 8);
            context.effects().playSound(target.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.2f);
        });
        context.effects().playSound(start, Sound.ITEM_TRIDENT_THROW, 1.0f, 1.1f);

        return AbilityResult.success();
    }
}
