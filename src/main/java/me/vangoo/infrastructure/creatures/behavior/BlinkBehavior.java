package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Door: блінк-скірмішер — періодично телепортується за спину найближчого потойбічного (через
 * безпечну локацію) і накладає Levitation. */
public final class BlinkBehavior implements CreatureBehavior {

    private final boolean apex;
    private long lastBlink;

    public BlinkBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        Player target = nearest(self, nearbyBeyonders);
        long now = System.currentTimeMillis();
        long cd = apex ? 3000 : 4500;
        if (now - lastBlink < cd) {
            return;
        }
        lastBlink = now;

        Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().multiply(2));
        Location safe = SafeLocations.passableNear(behind);

        self.getWorld().spawnParticle(Particle.PORTAL, self.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
        self.teleport(safe);
        self.getWorld().spawnParticle(Particle.PORTAL, safe.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);

        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0, false, false));
    }

    private Player nearest(LivingEntity self, List<Player> players) {
        Player best = players.get(0);
        double bestSq = best.getLocation().distanceSquared(self.getLocation());
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(self.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }
}
