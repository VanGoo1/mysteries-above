package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.PaperThrowDamage;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Спільна логіка кидка звичайного паперу для {@link PaperCutter} (Seq 8) та
 * {@link PaperWeaponry} (Seq 7) — обидві реалізують {@link PaperThrower}.
 * Кожна тримає власну мапу кулдаунів (екземпляр здібності спільний для pathway).
 */
final class PaperThrows {

    private static final double PROJECTILE_SPEED = 2.5;

    private PaperThrows() {
    }

    /** @return true, якщо кидок відбувся (для скасування ванільної інтеракції). */
    static boolean throwOnce(IAbilityContext context, Map<UUID, Long> cooldownByPlayer) {
        UUID casterId = context.getCasterId();
        Player player = context.getCasterPlayer();
        if (player == null) return false;

        long nowTick = player.getWorld().getFullTime();
        Long last = cooldownByPlayer.get(casterId);
        if (last != null && nowTick - last < PaperThrowDamage.THROW_COOLDOWN_TICKS) {
            return false;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.PAPER || mainHand.getAmount() < 1) {
            return false;
        }

        cooldownByPlayer.put(casterId, nowTick);

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        int damage = PaperThrowDamage.damageFor(context.getCasterBeyonder().getSequence());

        mainHand.setAmount(mainHand.getAmount() - 1);

        launchDagger(context, casterId, direction, damage);
        context.effects().playSound(eyeLoc, Sound.ENTITY_ARROW_SHOOT, 1.0f, 2.0f);
        return true;
    }

    private static void launchDagger(IAbilityContext context, UUID casterId, Vector direction, int damage) {
        Player casterPlayer = context.getCasterPlayer();
        if (casterPlayer == null) return;

        Snowball projectile = casterPlayer.launchProjectile(Snowball.class);
        projectile.setVelocity(direction.multiply(PROJECTILE_SPEED));

        startTrail(context, projectile);

        context.events().subscribeToTemporaryEvent(casterId,
                ProjectileHitEvent.class,
                e -> e.getEntity().getUniqueId().equals(projectile.getUniqueId()),
                e -> {
                    Entity hitEntity = e.getHitEntity();
                    if (hitEntity instanceof LivingEntity target) {
                        context.entity().damage(target.getUniqueId(), damage);
                        Location hitLoc = target.getLocation().add(0, 1, 0);
                        context.effects().spawnParticle(Particle.CRIT, hitLoc, 15, 0.3, 0.3, 0.3);
                        context.effects().spawnParticle(Particle.SWEEP_ATTACK, hitLoc, 3, 0.2, 0.2, 0.2);
                        context.effects().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.5f);
                    } else {
                        Location hitLoc = projectile.getLocation();
                        context.effects().spawnParticle(Particle.CRIT, hitLoc, 8, 0.1, 0.1, 0.1);
                        context.effects().playSound(hitLoc, Sound.ENTITY_ITEM_BREAK, 0.5f, 2.0f);
                    }
                },
                100
        );
    }

    private static void startTrail(IAbilityContext context, Snowball projectile) {
        final BukkitTask[] holder = new BukkitTask[1];
        final int[] ticks = {0};
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            ticks[0]++;
            if (projectile.isDead() || !projectile.isValid() || ticks[0] > 100) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            Location loc = projectile.getLocation();
            Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.6f);
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.02, 0.02, 0.02, 0, whiteDust);
            }
        }, 0L, 1L);
    }
}
