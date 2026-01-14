package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Appeasement extends ActiveAbility {

    private static final int BASE_RANGE = 4;
    private static final int COOLDOWN = 40;
    private static final int BASE_SANITY_REDUCTION = 25;
    private static final int BASE_REGEN_SECONDS = 7;

    @Override
    public String getName() {
        return "Умиротворення";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int regen = scaleValue(BASE_REGEN_SECONDS, sequence, SequenceScaler.ScalingStrategy.WEAK);
        int sanity = scaleValue(BASE_SANITY_REDUCTION, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        return String.format(
                "В радіусі %d бл. очищає розум, знімає негативні ефекти, " +
                        "дає Регенерацію I (%d с) та зменшує Sanity на %d.",
                range, regen, sanity
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence seq = context.getCasterBeyonder().getSequence();

        int range = scaleValue(BASE_RANGE, seq, SequenceScaler.ScalingStrategy.MODERATE);
        int sanityReduction = scaleValue(BASE_SANITY_REDUCTION, seq, SequenceScaler.ScalingStrategy.MODERATE);
        int regenSeconds = scaleValue(BASE_REGEN_SECONDS, seq, SequenceScaler.ScalingStrategy.WEAK);
        int regenTicks = regenSeconds * 20;

        Location center = context.getCasterLocation();
        Player caster = context.getCaster();

        // === ВІЗУАЛ НА СТАРТ ===
        context.spawnParticle(Particle.END_ROD, center, 80, range, 1.0, range);
        context.spawnParticle(Particle.SOUL, center, 40, range * 0.7, 0.8, range * 0.7);
        context.spawnParticle(Particle.HAPPY_VILLAGER, center, 60, range, 1.2, range);

        context.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.3f);
        context.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);

        // === КАСТЕР ===
        context.updateSanityLoss(caster.getUniqueId(), -sanityReduction);
        context.removeAllEffects(caster.getUniqueId());
        context.applyEffect(caster.getUniqueId(), PotionEffectType.REGENERATION, regenTicks, 0);

        boolean selfRescued = context.rescueFromRampage(caster.getUniqueId(), caster.getUniqueId());

        context.sendMessageToActionBar(
                caster,
                Component.text(
                        selfRescued
                                ? "✦ Розум стабілізовано"
                                : "✦ Внутрішній спокій відновлено"
                )
        );

        if (selfRescued) {
            showRescueEffects(context, caster);
        }

        // === ІНШІ ЦІЛІ ===
        List<LivingEntity> entities = context.getNearbyEntities(range);
        int affected = 0;

        for (LivingEntity entity : entities) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) continue;

            context.removeAllEffects(entity.getUniqueId());
            context.applyEffect(entity.getUniqueId(), PotionEffectType.REGENERATION, regenTicks, 0);

            Location loc = entity.getLocation();
            context.spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0, 1, 0), 12, 0.5, 0.5, 0.5);

            if (entity instanceof Player player) {
                context.updateSanityLoss(player.getUniqueId(), -sanityReduction);

                boolean rescued = context.rescueFromRampage(caster.getUniqueId(), player.getUniqueId());
                if (rescued) {
                    showRescueEffects(context, player);
                }

                context.sendMessageToActionBar(
                        player,
                        Component.text("✦ Розум очищено • +" + sanityReduction + " Sanity")
                );

                context.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
            }

            affected++;
        }

        // === ФІНАЛ ДЛЯ КАСТЕРА ===
        context.sendMessageToActionBar(
                caster,
                Component.text("✦ Умиротворення • Цілей: " + affected)
        );

        return AbilityResult.success();
    }

    private void showRescueEffects(IAbilityContext context, Player player) {
        Location loc = player.getLocation();
        context.spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 25, 0.4, 0.8, 0.4);
        context.spawnParticle(Particle.SOUL, loc, 20, 0.3, 0.5, 0.3);
        context.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }
}
