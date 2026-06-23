package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Clown — Paper Cutter (Паперовий різак)
 *
 * Hardens paper with spiritual energy, turning it into lethal throwing daggers.
 * Requires Paper in hand. Shift+RMB fires a fan of 3 blades.
 */
public class PaperCutter extends ActiveAbility {

    private static final int BASE_COST = 40;
    private static final int FAN_COST = 120;
    private static final int BASE_COOLDOWN = 3;
    private static final int BASE_DAMAGE = 6;
    private static final double PROJECTILE_SPEED = 2.5;

    // Track active paper projectiles
    private static final Set<UUID> activePaperProjectiles = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "Паперовий різак";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int damage = scaleValue(BASE_DAMAGE, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        return "Зміцнений духовною енергією папір стає гострим як сталь. " +
                "Кидає паперовий кинджал (" + damage + " шкоди). " +
                "Shift+ПКМ — віяло з 3 кинджалів. Потребує папір в руці.";
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
    protected boolean canExecute(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        var mainHand = context.playerData().getMainHandItem(casterId);
        if (mainHand == null || mainHand.getType() != Material.PAPER) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "✗ Потрібно тримати папір у руці!");
            return false;
        }

        boolean isSneaking = context.playerData().isSneaking(casterId);
        int requiredPaper = isSneaking ? 3 : 1;
        if (mainHand.getAmount() < requiredPaper) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "✗ Недостатньо паперу! (потрібно " + requiredPaper + ")");
            return false;
        }

        return true;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        boolean isSneaking = context.playerData().isSneaking(casterId);
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        Location eyeLoc = context.playerData().getEyeLocation(casterId);
        if (eyeLoc == null) return AbilityResult.failure("Неможливо визначити позицію");

        Vector direction = eyeLoc.getDirection().normalize();

        // Consume paper
        var mainHand = context.playerData().getMainHandItem(casterId);
        int consumed = isSneaking ? 3 : 1;
        mainHand.setAmount(mainHand.getAmount() - consumed);

        if (isSneaking) {
            // Fan of 3 daggers
            launchPaperDagger(context, casterId, eyeLoc, rotateVector(direction, -15), damage);
            launchPaperDagger(context, casterId, eyeLoc, direction.clone(), damage);
            launchPaperDagger(context, casterId, eyeLoc, rotateVector(direction, 15), damage);

            context.effects().playSound(eyeLoc, Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.8f);
            context.effects().playSound(eyeLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
        } else {
            // Single dagger
            launchPaperDagger(context, casterId, eyeLoc, direction, damage);
            context.effects().playSound(eyeLoc, Sound.ENTITY_ARROW_SHOOT, 1.0f, 2.0f);
        }

        return AbilityResult.success();
    }

    private void launchPaperDagger(IAbilityContext context, UUID casterId, Location origin,
                                   Vector direction, int damage) {
        var casterPlayer = context.getCasterPlayer();
        if (casterPlayer == null) return;

        Snowball projectile = casterPlayer.launchProjectile(Snowball.class);
        projectile.setVelocity(direction.multiply(PROJECTILE_SPEED));
        activePaperProjectiles.add(projectile.getUniqueId());

        // Trail particles
        context.scheduling().scheduleRepeating(() -> {
            if (projectile.isDead() || !projectile.isValid()) return;

            Location loc = projectile.getLocation();
            Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.6f);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.02, 0.02, 0.02, 0, whiteDust);
        }, 0L, 1L);

        // Hit detection
        context.events().subscribeToTemporaryEvent(casterId,
                ProjectileHitEvent.class,
                e -> e.getEntity().getUniqueId().equals(projectile.getUniqueId()),
                e -> {
                    activePaperProjectiles.remove(projectile.getUniqueId());

                    Entity hitEntity = e.getHitEntity();
                    if (hitEntity instanceof LivingEntity target) {
                        context.entity().damage(target.getUniqueId(), damage);

                        Location hitLoc = target.getLocation().add(0, 1, 0);
                        context.effects().spawnParticle(Particle.CRIT, hitLoc, 15, 0.3, 0.3, 0.3);
                        context.effects().spawnParticle(Particle.SWEEP_ATTACK, hitLoc, 3, 0.2, 0.2, 0.2);
                        context.effects().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.5f);
                    } else {
                        // Hit a block
                        Location hitLoc = projectile.getLocation();
                        context.effects().spawnParticle(Particle.CRIT, hitLoc, 8, 0.1, 0.1, 0.1);
                        context.effects().playSound(hitLoc, Sound.ENTITY_ITEM_BREAK, 0.5f, 2.0f);
                    }
                },
                100 // 5 second timeout
        );
    }

    private Vector rotateVector(Vector vec, double angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = vec.getX() * cos - vec.getZ() * sin;
        double z = vec.getX() * sin + vec.getZ() * cos;
        return new Vector(x, vec.getY(), z).normalize().multiply(vec.length());
    }

    @Override
    public void cleanUp() {
        activePaperProjectiles.clear();
    }
}
