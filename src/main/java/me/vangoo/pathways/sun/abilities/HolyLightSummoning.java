package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.UUID;

public class HolyLightSummoning extends ActiveAbility {

    private static final double MAX_RANGE = 20.0;
    private static final double PILLAR_HEIGHT = 15.0;
    private static final double PILLAR_RADIUS = 1.1;
    // На Seq 5 і сильніше здібність переростає у «Стовп святості»: більший радіус/шкода,
    // менший кулдаун і Нічне бачення кастеру. Раніше це був окремий клас LightOfHoliness —
    // злитий сюди, бо відрізнявся лише числами (шкода й так скейлиться від Sequence) + одним бафом.
    private static final int ENHANCED_FROM_SEQUENCE = 5;
    private static final int NIGHT_VISION_TICKS = 1_000_000; // практично постійне, оновлюється кожним кастом

    private boolean isEnhanced(Sequence sequence) {
        return sequence.level() <= ENHANCED_FROM_SEQUENCE;
    }

    private double aoeRadius(Sequence sequence) {
        return isEnhanced(sequence) ? 6.0 : 4.0;
    }

    private int baseDamage(Sequence sequence) {
        return scaleValue(isEnhanced(sequence) ? 16 : 10, sequence, SequenceScaler.ScalingStrategy.STRONG);
    }

    private int fireTicks(Sequence sequence) {
        return isEnhanced(sequence) ? 100 : 80;
    }

    private int weaknessTicks(Sequence sequence) {
        return isEnhanced(sequence) ? 120 : 100;
    }

    @Override
    public String getName() {
        return "Святе світло";
    }

    @Override
    public String getDescription(Sequence sequence) {
        double radius = aoeRadius(sequence);
        int damage = baseDamage(sequence);
        if (isEnhanced(sequence)) {
            return String.format(
                    "§fОпускає посилений стовп святого вогню з неба на ціль (радіус §e%.0f §fбл) і дарує вам " +
                            "§bНічне бачення§f.\n§7Шкода: §c%d §7(проти темних/нежиті — повна, проти інших — знижена), " +
                            "підпалює й послаблює.",
                    radius, damage
            );
        }
        return String.format(
                "§fОпускає стовп святого вогню з неба на ціль (радіус §e%.0f §fбл). " +
                        "§7Шкода: §c%d §7(проти темних/нежиті — повна, проти інших — знижена), підпалює й послаблює.",
                radius, damage
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 55;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return isEnhanced(sequence) ? 30 : 35;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі для стовпа світла");
        }

        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Location center = targetOpt.get().getLocation();
        double aoeRadius = aoeRadius(sequence);
        int baseDamage = baseDamage(sequence);

        int hit = 0;
        if (center.getWorld() != null) {
            for (Entity entity : center.getWorld().getNearbyEntities(center, aoeRadius, aoeRadius, aoeRadius)) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (living.getUniqueId().equals(casterId)) continue;

                boolean darkTarget = HolyTargetClassifier.isDarkOrUndead(living, context);
                double multiplier = HolyAffinity.damageMultiplier(darkTarget) * context.amplification().getDamageMultiplier(casterId);
                int damage = (int) Math.ceil(baseDamage * multiplier);

                context.entity().damage(living.getUniqueId(), damage);
                context.entity().applyPotionEffect(living.getUniqueId(), PotionEffectType.WEAKNESS, weaknessTicks(sequence), 0);
                living.setFireTicks(fireTicks(sequence));
                hit++;
            }
        }

        if (isEnhanced(sequence)) {
            context.entity().applyPotionEffect(casterId, PotionEffectType.NIGHT_VISION, NIGHT_VISION_TICKS, 0);
        }

        Color sunlight = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        playPillarEffect(context, center, aoeRadius, sunlight);
        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "☀ Стовп святого світла вразив цілей: " + hit);

        return AbilityResult.success();
    }

    private void playPillarEffect(IAbilityContext context, Location center, double aoeRadius, Color sunlight) {
        Particle.DustOptions gold = new Particle.DustOptions(sunlight, 1.6f);

        context.effects().playPillarEffect(center, PILLAR_HEIGHT, PILLAR_RADIUS, sunlight, 35);
        context.effects().playCircleEffect(center, aoeRadius, Particle.FLAME, 30);
        context.effects().playExplosionRingEffect(center, aoeRadius * 0.6, Particle.DUST, gold);
        context.effects().playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 0.8f);
        context.effects().playSound(center, Sound.ITEM_TOTEM_USE, 1.0f, 1.3f);
        context.effects().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
    }
}
