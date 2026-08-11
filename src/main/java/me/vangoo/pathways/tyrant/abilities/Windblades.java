package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Sequence 6: Леза Вітру. Благословенний Вітром стискає повітря у невидимий різальний
 * порив — рей-трейс до першої істоти на лінії зору, шкода + легкий відкид. У присіді
 * вивільняє віяло з трьох лез, що б'ють ширшим сектором.
 */
public class Windblades extends ActiveAbility {

    private static final int BASE_DAMAGE = 5;
    private static final double MAX_RANGE = 30.0;
    private static final double KNOCKBACK = 0.6;
    private static final int COOLDOWN = 10;
    private static final int SPIRITUALITY_COST = 40;
    private static final double FAN_SPREAD_DEGREES = 20.0;

    @Override
    public String getName() {
        return "Леза Вітру";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fСтискає повітря у невидиме різальне лезо — §c%d §fшкоди першій істоті " +
                        "на лінії зору (%d блоків). §7Присівши §f— віяло з трьох лез ширшим сектором.",
                damage, (int) MAX_RANGE);
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

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();

        context.effects().playSound(eye, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 1.0f);

        if (player.isSneaking()) {
            slash(context, player, eye, rotateYaw(forward, -FAN_SPREAD_DEGREES), damage);
            slash(context, player, eye, forward, damage);
            slash(context, player, eye, rotateYaw(forward, FAN_SPREAD_DEGREES), damage);
        } else {
            slash(context, player, eye, forward, damage);
        }

        return AbilityResult.success();
    }

    /** Одне лезо: рей-трейс уздовж {@code direction}, промінь GUST, шкода+відкид при влучанні. */
    private void slash(IAbilityContext context, Player caster, Location eye, Vector direction, int damage) {
        RayTraceResult result = eye.getWorld().rayTrace(
                eye, direction, MAX_RANGE, FluidCollisionMode.NEVER, true, 0.5,
                e -> e instanceof LivingEntity && !e.getUniqueId().equals(caster.getUniqueId()));

        double reach = result != null ? eye.distance(result.getHitPosition().toLocation(eye.getWorld())) : MAX_RANGE;
        Location end = eye.clone().add(direction.clone().multiply(reach));
        context.effects().playBeamEffect(eye, end, Particle.SMALL_GUST, 0.15, 4);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, caster);
            target.setVelocity(target.getVelocity().add(direction.clone().setY(0.2).normalize().multiply(KNOCKBACK)));
            Location hit = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            context.effects().spawnParticle(Particle.SWEEP_ATTACK, hit, 1);
            context.effects().playSound(hit, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        }
    }

    /** Повертає вектор на {@code degrees} навколо вертикальної осі (розкид віяла). */
    private static Vector rotateYaw(Vector v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }
}
