package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Visionary: стелс-засідка — поки гравець далеко, істота невидима й підкрадається; при зближенні
 * розкривається й накладає ментальний сплеск (Nausea + Blindness + Darkness) + малий дрен розсудку. */
public final class AmbushBehavior implements CreatureBehavior {

    private final boolean apex;
    private final BeyonderService beyonderService;

    public AmbushBehavior(boolean apex, BeyonderService beyonderService) {
        this.apex = apex;
        this.beyonderService = beyonderService;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        Player target = nearest(self, nearbyBeyonders);
        double dist = target.getLocation().distance(self.getLocation());

        if (dist > 4.0) {
            self.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
            self.setCustomNameVisible(false);
            return;
        }

        self.removePotionEffect(PotionEffectType.INVISIBILITY);
        self.setCustomNameVisible(true);

        int nauseaDur = apex ? 100 : 70;
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDur, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));

        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            Beyonder b = beyonderService.getBeyonder(target.getUniqueId());
            if (b != null) {
                b.increaseSanityLoss(1);
            }
        }
        self.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.01);
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
