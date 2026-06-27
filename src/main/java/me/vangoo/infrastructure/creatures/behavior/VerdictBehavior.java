package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Justiciar: постійна аура вироку — поряд із істотою потойбічні «скуті судом»
 * (Slowness + Mining Fatigue + Weakness). На apex Slowness II. */
public final class VerdictBehavior implements CreatureBehavior {

    private final boolean apex;

    public VerdictBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        int slowAmp = apex ? 1 : 0;
        for (Player p : nearbyBeyonders) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slowAmp, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
        }
        self.getWorld().spawnParticle(Particle.WAX_OFF, self.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0);
    }
}
