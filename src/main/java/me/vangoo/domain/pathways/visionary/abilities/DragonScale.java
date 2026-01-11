package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DragonScale extends ActiveAbility {

    private static final int BASE_DURATION_SECONDS = 21;
    private static final int BASE_COST = 150;
    private static final int BASE_COOLDOWN = 75;

    @Override
    public String getName() {
        return "Луска дракона";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int duration = calculateDuration(userSequence.level());
        return "Психологічна маніфестація луски дракона. Дає " +
                "Опір II та Вогнестійкість II на " + duration + " секунд.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        double multiplier = SequenceScaler.calculateMultiplier(userSequence.level(), ScalingStrategy.WEAK);
        return Math.max(60, (int) (BASE_COOLDOWN / multiplier));
    }

    private int calculateDuration(int sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(sequence, ScalingStrategy.MODERATE);
        return (int) (BASE_DURATION_SECONDS * multiplier);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        Beyonder beyonder = context.getCasterBeyonder();
        int sequenceVal = beyonder.getSequence().level();

        if (sequenceVal > 6) {
            return AbilityResult.failure("Ваш рівень послідовності занизький для цієї форми.");
        }

        int durationSeconds = calculateDuration(sequenceVal);
        int durationTicks = durationSeconds * 20;

        // Resistance II (-40% вхідної шкоди)
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1));
        // Fire Resistance (Імунітет до лави/вогню)
        caster.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0));

        // Візуал: Більш "важкий" звук
        context.playSoundToCaster(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);
        context.playSoundToCaster(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.5f);
        caster.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Психологічна луска вкриває ваше тіло!");

        caster.getWorld().spawnParticle(Particle.FLAME,
                caster.getLocation().add(0, 1, 0),
                30, 0.5, 1, 0.5, 0.02);

        caster.getWorld().spawnParticle(Particle.LAVA,
                caster.getLocation().add(0, 1, 0),
                10, 0.5, 1, 0.5);

        return AbilityResult.success();
    }
}