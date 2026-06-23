package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Sequence 7: Magician — Air Bullet (Повітряна куля)
 *
 * Compress air into an invisible projectile — the "finger gun" of Klein Moretti.
 * Shift for a powered shot with double damage and cost.
 */
public class AirBullet extends ActiveAbility {

    private static final int BASE_COST = 35;
    private static final int BASE_COOLDOWN = 2;
    private static final int BASE_DAMAGE = 5;
    private static final double MAX_RANGE = 25.0;
    private static final double KNOCKBACK_STRENGTH = 0.8;

    @Override
    public String getName() {
        return "Повітряна куля";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int damage = scaleValue(BASE_DAMAGE, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        return "Стискає повітря у невидимий снаряд (" + damage + " шкоди, " +
                (int) MAX_RANGE + " блоків). Shift — посилений постріл (x2 шкода/вартість).";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        boolean isPowered = context.playerData().isSneaking(casterId);

        Location eyeLoc = context.playerData().getEyeLocation(casterId);
        if (eyeLoc == null || eyeLoc.getWorld() == null) {
            return AbilityResult.failure("Неможливо визначити позицію");
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        if (isPowered) {
            damage *= 2;
        }

        Vector direction = eyeLoc.getDirection().normalize();

        // Sound — gunshot simulation
        context.effects().playSound(eyeLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, isPowered ? 0.5f : 1.0f);
        if (isPowered) {
            context.effects().playSound(eyeLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        }

        // Ray trace for hit detection
        RayTraceResult result = eyeLoc.getWorld().rayTrace(
                eyeLoc,
                direction,
                MAX_RANGE,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                e -> e instanceof LivingEntity && !e.getUniqueId().equals(casterId)
        );

        // Trail particles along the path
        double trailDistance = MAX_RANGE;
        if (result != null) {
            trailDistance = eyeLoc.distance(result.getHitPosition().toLocation(eyeLoc.getWorld()));
        }

        playAirTrail(context, eyeLoc, direction, trailDistance, isPowered);

        // Hit processing
        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            int finalDamage = damage;

            // Delay damage slightly for visual sync
            context.scheduling().scheduleDelayed(() -> {
                context.entity().damage(target.getUniqueId(), finalDamage);

                // Knockback
                Vector knockback = direction.clone().setY(0.3).normalize().multiply(KNOCKBACK_STRENGTH);
                if (isPowered) knockback.multiply(1.5);
                context.entity().setVelocity(target.getUniqueId(), knockback);

                // Impact effects
                Location hitLoc = target.getLocation().add(0, 1, 0);
                context.effects().spawnParticle(Particle.EXPLOSION, hitLoc, isPowered ? 3 : 1, 0.1, 0.1, 0.1);
                context.effects().spawnParticle(Particle.CLOUD, hitLoc, 10, 0.2, 0.2, 0.2);
                context.effects().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5f, 1.0f);
            }, 2L);

        } else if (result != null && result.getHitBlock() != null) {
            // Block impact
            Location impactLoc = result.getHitPosition().toLocation(eyeLoc.getWorld());
            context.scheduling().scheduleDelayed(() -> {
                context.effects().spawnParticle(Particle.CLOUD, impactLoc, 8, 0.1, 0.1, 0.1);
                context.effects().playSound(impactLoc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.5f, 0.8f);
            }, 2L);
        }

        return AbilityResult.success();
    }

    private void playAirTrail(IAbilityContext context, Location start, Vector direction,
                              double distance, boolean powered) {
        double step = 0.5;
        int steps = (int) (distance / step);

        for (int i = 0; i < steps; i++) {
            int tick = i / 5; // Group particles by time for speed illusion
            double dist = i * step;

            context.scheduling().scheduleDelayed(() -> {
                Location point = start.clone().add(direction.clone().multiply(dist));
                context.effects().spawnParticle(Particle.CLOUD, point, powered ? 3 : 1, 0.02, 0.02, 0.02);
                if (powered) {
                    context.effects().spawnParticle(Particle.CRIT, point, 1, 0.01, 0.01, 0.01);
                }
            }, tick);
        }
    }
}
