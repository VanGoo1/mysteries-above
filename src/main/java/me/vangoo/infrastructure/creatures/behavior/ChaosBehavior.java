package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Error: трикстер невдачі — періодично робить одне випадкове: короткий телепорт гравця, Weakness,
 * Mining Fatigue або Bad Omen; дуже зрідка крихітний дрен розсудку. */
public final class ChaosBehavior implements CreatureBehavior {

    private final boolean apex;
    private final BeyonderService beyonderService;
    private long lastAct;

    public ChaosBehavior(boolean apex, BeyonderService beyonderService) {
        this.apex = apex;
        this.beyonderService = beyonderService;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        long now = System.currentTimeMillis();
        long cd = apex ? 2500 : 3500;
        if (now - lastAct < cd) {
            return;
        }
        lastAct = now;

        Player target = nearbyBeyonders.get(ThreadLocalRandom.current().nextInt(nearbyBeyonders.size()));
        switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> {
                Location l = target.getLocation().clone().add(rand(), 0, rand());
                target.teleport(SafeLocations.passableNear(l));
            }
            case 1 -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false));
            case 2 -> target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 0, false, false));
            default -> target.addPotionEffect(new PotionEffect(PotionEffectType.BAD_OMEN, 100, 0, false, false));
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            Beyonder b = beyonderService.getBeyonder(target.getUniqueId());
            if (b != null) {
                b.increaseSanityLoss(1);
            }
        }
        self.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
    }

    private double rand() {
        return ThreadLocalRandom.current().nextDouble(-5.0, 5.0);
    }
}
