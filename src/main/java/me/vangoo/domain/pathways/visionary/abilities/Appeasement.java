package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Appeasement extends ActiveAbility {
    private static final int RANGE = 4;
    private static final int COOLDOWN = 40;
    private static final int SANITY_REDUCTION = 25;
    private static final int REGENERATION_SECONDS = 7;


    @Override
    public String getName() {
        return "Умиротворення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int range = scaleValue(RANGE, userSequence,
                SequenceScaler.ScalingStrategy.MODERATE);
        int regenSeconds = scaleValue(REGENERATION_SECONDS, userSequence,
                SequenceScaler.ScalingStrategy.WEAK);
        int sanityReduction = scaleValue(SANITY_REDUCTION, userSequence,
                SequenceScaler.ScalingStrategy.MODERATE);

        return String.format(
                "В радіусі %d блоків прибирає всі небажані ефекти, " +
                        "надає Регенерацію I на %d секунд та зменшує втрату контролю на %d.",
                range, regenSeconds, sanityReduction
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 100;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence userSequence = context.getCasterBeyonder().getSequence();

        // Масштабовані значення на основі поточної послідовності
        int range = scaleValue(RANGE, userSequence,
                SequenceScaler.ScalingStrategy.MODERATE);
        int sanityReduction = scaleValue(SANITY_REDUCTION, userSequence,
                SequenceScaler.ScalingStrategy.MODERATE);
        int regenSeconds = scaleValue(REGENERATION_SECONDS, userSequence,
                SequenceScaler.ScalingStrategy.WEAK);

        Location casterLoc = context.getCasterLocation();

        // Візуальні ефекти
        context.spawnParticle(Particle.END_ROD, casterLoc, 100, range, 1, range);
        context.spawnParticle(Particle.ENCHANT, casterLoc, 50,
                range * 0.7, 1, range * 0.7);
        context.spawnParticle(Particle.FIREWORK, casterLoc, 50,
                range * 0.8, 1.5, range * 0.8);
        context.spawnParticle(Particle.SOUL, casterLoc, 30, range, 0.5, range);

        // Звуки
        context.playSound(casterLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        context.playSound(casterLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        context.playSound(casterLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);

        // Знаходимо сутності
        List<LivingEntity> nearbyEntities = context.getNearbyEntities(range);
        int affectedCount = 0;

        // Самолікування
        boolean selfRescued = context.rescueFromRampage(
                context.getCasterId(),
                context.getCasterId()
        );
        if (selfRescued) {
            showRescueEffects(context, context.getCaster());
        }

        int regenDurationTicks = 20 * regenSeconds;

        for (LivingEntity entity : nearbyEntities) {
            context.removeAllEffects(entity.getUniqueId());
            context.applyEffect(
                    entity.getUniqueId(),
                    PotionEffectType.REGENERATION,
                    regenDurationTicks,
                    0
            );

            if (entity instanceof Player) {
                context.updateSanityLoss(entity.getUniqueId(), -sanityReduction);

                boolean rescued = context.rescueFromRampage(
                        context.getCasterId(),
                        entity.getUniqueId()
                );
                if (rescued) {
                    showRescueEffects(context, (Player) entity);
                }

                Location entityLoc = entity.getLocation();
                context.spawnParticle(Particle.HEART, entityLoc, 5, 0.5, 0.5, 0.5);
                context.spawnParticle(Particle.HAPPY_VILLAGER,
                        entityLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5);
                context.playSound(entityLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);

                context.sendMessage(entity.getUniqueId(),
                        ChatColor.GREEN + "✦ Ви відчуваєте спокій і ясність розуму");
            } else {
                Location entityLoc = entity.getLocation();
                context.spawnParticle(Particle.HAPPY_VILLAGER,
                        entityLoc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                context.spawnParticle(Particle.END_ROD,
                        entityLoc.clone().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3);
            }

            affectedCount++;
        }

        context.sendMessageToCaster(
                ChatColor.GREEN + "✦ Умиротворення вплинуло на " +
                        affectedCount + " істот"
        );

        return AbilityResult.success();
    }

    private void showRescueEffects(IAbilityContext context, Player rescued) {
        Location loc = rescued.getLocation();

        // Intense healing particles
        context.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 0.5, 1.0, 0.5);
        context.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);

        // Triumphant sound
        context.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }
}