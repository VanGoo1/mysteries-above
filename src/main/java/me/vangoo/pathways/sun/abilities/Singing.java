package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

public class Singing extends ActiveAbility {

    private static final int BASE_RANGE = 8;
    private static final int BASE_DURATION_SECONDS = 10;
    private static final int COOLDOWN = 20;

    private static final PotionEffectType[] NEGATIVE_EFFECTS = {
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA,
            PotionEffectType.HUNGER,
            PotionEffectType.DARKNESS
    };

    @Override
    public String getName() {
        return "Спів";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int duration = scaleValue(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int amplifier = calculateAmplifier(sequence);

        return String.format(
                "§fОбдаровує союзників поруч Регенерацією, Силою %d та Швидкістю %d, " +
                        "знімаючи негативні ефекти.\n§7Радіус: %d бл. · Тривалість: %d с.",
                amplifier + 1, amplifier + 1, range, duration
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 25;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence sequence = context.getCasterBeyonder().getSequence();
        UUID casterId = context.getCasterId();

        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int durationTicks = scaleValue(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE) * 20;
        int amplifier = calculateAmplifier(sequence);

        Location center = context.getCasterLocation();
        context.effects().spawnParticle(Particle.NOTE, center, 30, range * 0.5, 1.0, range * 0.5);
        context.effects().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);

        List<Player> nearby = context.targeting().getNearbyPlayers(range);

        buffPlayer(context, casterId, durationTicks, amplifier);
        for (Player ally : nearby) {
            buffPlayer(context, ally.getUniqueId(), durationTicks, amplifier);
        }

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Спів • Союзників зачаровано: " + nearby.size()));

        return AbilityResult.success();
    }

    private void buffPlayer(IAbilityContext context, UUID targetId, int durationTicks, int amplifier) {
        for (PotionEffectType negative : NEGATIVE_EFFECTS) {
            context.entity().removePotionEffect(targetId, negative);
        }
        context.entity().applyPotionEffect(targetId, PotionEffectType.REGENERATION, durationTicks, 0);
        context.entity().applyPotionEffect(targetId, PotionEffectType.STRENGTH, durationTicks, amplifier);
        context.entity().applyPotionEffect(targetId, PotionEffectType.SPEED, durationTicks, amplifier);
    }

    private int calculateAmplifier(Sequence sequence) {
        int power = SequenceScaler.getSequencePower(sequence.level());
        if (power >= 8) return 2;
        if (power >= 5) return 1;
        return 0;
    }
}
