package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Fool: контроль — періодично притягує найближчого потойбічного «нитками» + Slowness; на низькому
 * HP разово прикликає слабких «ляльок» (зомбі); зрідка малий дрен розсудку. */
public final class ControlBehavior implements CreatureBehavior {

    private final boolean apex;
    private final BeyonderService beyonderService;
    private long lastPull;
    private boolean summoned;

    public ControlBehavior(boolean apex, BeyonderService beyonderService) {
        this.apex = apex;
        this.beyonderService = beyonderService;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        Player target = nearest(self, nearbyBeyonders);
        long now = System.currentTimeMillis();
        if (now - lastPull >= 3000) {
            lastPull = now;
            Vector dir = self.getLocation().toVector().subtract(target.getLocation().toVector());
            if (dir.lengthSquared() > 0.01) {
                dir.normalize().multiply(apex ? 1.2 : 0.9).setY(0.2);
                target.setVelocity(target.getVelocity().add(dir));
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
            if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                Beyonder b = beyonderService.getBeyonder(target.getUniqueId());
                if (b != null) {
                    b.increaseSanityLoss(1);
                }
            }
        }

        if (!summoned) {
            AttributeInstance maxHp = self.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHp != null ? maxHp.getValue() : self.getHealth();
            if (self.getHealth() <= max * 0.35) {
                summoned = true;
                int n = apex ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    Entity z = self.getWorld().spawnEntity(self.getLocation(), EntityType.ZOMBIE);
                    if (z instanceof LivingEntity puppet) {
                        puppet.setCustomName("§8Лялька");
                        puppet.setCustomNameVisible(false);
                    }
                }
            }
        }
        self.getWorld().spawnParticle(Particle.WITCH, self.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
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
