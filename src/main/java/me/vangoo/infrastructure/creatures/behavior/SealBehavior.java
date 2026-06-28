package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Door: «запечатаний шлях» — періодично «зачиняє двері» навколо найближчого потойбічного:
 * Slowness + коротка Darkness + збіжні портал-частки. Контроль без телепорту й без шкоди
 * (заміна сильному блінку за спину). */
public final class SealBehavior implements CreatureBehavior {

    private final boolean apex;
    private long lastSeal;

    public SealBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        long now = System.currentTimeMillis();
        long cd = apex ? 5000 : 7000;
        if (now - lastSeal < cd) {
            return;
        }
        lastSeal = now;

        Player target = nearest(self, nearbyBeyonders);

        int slowAmplifier = apex ? 1 : 0; // apex — Slowness II, common — Slowness I
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, slowAmplifier, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));

        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0, 1, 0),
                40, 0.6, 1.0, 0.6, 0.05);
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
