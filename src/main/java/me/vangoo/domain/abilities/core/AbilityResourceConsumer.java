package me.vangoo.domain.abilities.core;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.MasteryProgressionCalculator;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.Spirituality;

/**
 * Utility class for consuming ability resources (spirituality) and granting mastery.
 * Used by abilities with deferred execution to manually consume resources after
 * the actual execution completes.
 */
public class AbilityResourceConsumer {

    /**
     * Consume spirituality and grant mastery for an ability execution.
     * This should be called ONLY for deferred abilities after they actually execute.
     *
     * @param ability The ability that was executed
     * @param beyonder The beyonder who executed the ability
     * @param context The execution context
     * @return true if resources were consumed successfully, false if insufficient spirituality
     */
    public static boolean consumeResources(Ability ability, Beyonder beyonder, IAbilityContext context) {
        if (ability.getType() != AbilityType.ACTIVE) {
            return true; // Non-active abilities don't consume resources
        }

        int cost = ability.getSpiritualityCost();

        // Check if beyonder has enough spirituality
        if (!beyonder.getSpirituality().hasSufficient(cost)) {
            return false;
        }

        // Consume spirituality
        Spirituality currentSpirituality = beyonder.getSpirituality();
        beyonder.setSpirituality(currentSpirituality.decrement(cost));

        // Grant mastery
        double masteryGain = MasteryProgressionCalculator.calculateMasteryGain(
                cost,
                beyonder.getSequence()
        );

        if (masteryGain > 0) {
            Mastery currentMastery = beyonder.getMastery();
            beyonder.setMastery(currentMastery.add(masteryGain));
        }

        // Set cooldown
        context.setCooldown(ability, ability.getCooldown(beyonder.getSequence()));

        // Check for critical spirituality
        if (beyonder.getSpirituality().isCritical()) {
            beyonder.increaseSanityLoss(2);
        }

        return true;
    }

    /**
     * Check if beyonder has enough spirituality to execute an ability.
     *
     * @param ability The ability to check
     * @param beyonder The beyonder
     * @return true if has enough spirituality
     */
    public static boolean hasSufficientResources(Ability ability, Beyonder beyonder) {
        if (ability.getType() != AbilityType.ACTIVE) {
            return true;
        }

        int cost = ability.getSpiritualityCost();
        return beyonder.getSpirituality().hasSufficient(cost);
    }
}
