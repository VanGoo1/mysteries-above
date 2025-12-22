package me.vangoo.application.abilities;

import me.vangoo.domain.valueobjects.SanityLoss;

/**
 * Result of sanity loss check during ability execution
 */
public record SanityLossCheckResult(
        boolean passed,
        SanityLoss sanityLoss,
        SanityPenalty penalty,
        String message
) {

    public static SanityLossCheckResult passed(SanityLoss sanityLoss) {
        return new SanityLossCheckResult(true, sanityLoss, SanityPenalty.none(), null);
    }

    public static SanityLossCheckResult failed(SanityLoss sanityLoss, SanityPenalty penalty, String message) {
        return new SanityLossCheckResult(false, sanityLoss, penalty, message);
    }

    public boolean canExecuteAbility() {
        return passed;
    }
}