package me.vangoo.domain.pathways.error.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class FractureOfRealitiesAbility extends Ability {

    private static final int RADIUS = 8;
    private static final int DURATION_TICKS = 80; // 4 seconds

    @Override
    public String getName() {
        return "Fracture of Realities";
    }

    @Override
    public String getDescription() {
        return "Створює аномалію навколо, що спотворює реальність для всіх в радіусі " + RADIUS + " блоків";
    }

    @Override
    public int getSpiritualityCost() {
        return 70;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Location casterLoc = context.getCasterLocation();

        // Create visual anomaly effect
        context.spawnParticle(Particle.PORTAL, casterLoc, 100, 0, 0, 0);
        context.spawnParticle(Particle.DRAGON_BREATH, casterLoc, 50, RADIUS, 1, RADIUS);
        context.spawnParticle(Particle.END_ROD, casterLoc, 30, RADIUS * 0.5, 2, RADIUS * 0.5);

        // Play ominous sounds
        context.playSound(casterLoc, Sound.BLOCK_PORTAL_AMBIENT, 1.5f, 0.5f);
        context.playSound(casterLoc, Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.8f);

        // Apply confusion and distortion effects to nearby entities
        List<org.bukkit.entity.LivingEntity> nearbyEntities = context.getNearbyEntities(RADIUS);

        for (org.bukkit.entity.LivingEntity entity : nearbyEntities) {
            // Apply nausea/confusion effect
            context.applyEffect(entity.getUniqueId(), PotionEffectType.NAUSEA, DURATION_TICKS, 0);
            // Apply slowness to represent reality distortion
            context.applyEffect(entity.getUniqueId(), PotionEffectType.SLOWNESS, DURATION_TICKS, 1);
        }

        // Store entities for cleanup (this would need to be stored in ability state)
        // For now, we'll use a simple approach - schedule removal
        context.scheduleDelayed(() -> {
            // Remove effects after duration
            for (org.bukkit.entity.LivingEntity entity : nearbyEntities) {
                context.removeEffect(entity.getUniqueId(), PotionEffectType.NAUSEA);
                context.removeEffect(entity.getUniqueId(), PotionEffectType.SLOWNESS);
            }
        }, DURATION_TICKS);

        context.sendMessageToCaster("Реальність навколо вас спотворюється! Всі в радіусі відчувають хаос.");

        return AbilityResult.success();
    }

    @Override
    public int getCooldown() {
        return 6;
    }

}
