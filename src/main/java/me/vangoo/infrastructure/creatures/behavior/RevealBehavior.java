package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** WhiteTower: аура викриття — Glowing (підсвічує крізь стіни) + зрідка спалах Blindness;
 * періодично кидає світловий снаряд (Snowball, для хараса/відкидання) у найближчого. */
public final class RevealBehavior implements CreatureBehavior {

    private final boolean apex;
    private long lastShot;

    public RevealBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        for (Player p : nearbyBeyonders) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false));
            }
        }
        long now = System.currentTimeMillis();
        long cd = apex ? 3500 : 5000;
        if (now - lastShot >= cd) {
            lastShot = now;
            Player target = nearbyBeyonders.get(0);
            Snowball ball = self.getWorld().spawn(self.getEyeLocation(), Snowball.class);
            ball.setShooter(self);
            Vector dir = target.getEyeLocation().toVector().subtract(self.getEyeLocation().toVector());
            if (dir.lengthSquared() > 0.001) {
                ball.setVelocity(dir.normalize().multiply(1.6));
            }
        }
        self.getWorld().spawnParticle(Particle.END_ROD, self.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0.01);
    }
}
