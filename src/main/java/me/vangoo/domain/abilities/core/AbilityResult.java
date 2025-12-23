package me.vangoo.domain.abilities.core;

import me.vangoo.application.abilities.SanityPenalty;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;

import javax.annotation.Nullable;

public class AbilityResult {
    private final boolean success;
    private final String message;
    private final FailureReason failureReason;
    private final SequenceBasedSuccessChance successChance;
    private final SanityPenalty sanityPenalty;

    /**
     * Reason why an ability failed
     */
    public enum FailureReason {
        NONE,                   // Success
        INSUFFICIENT_RESOURCES, // Not enough spirituality/resources
        COOLDOWN,              // On cooldown
        INVALID_TARGET,        // No target or invalid target
        SEQUENCE_RESISTANCE,   // Failed due to sequence-based chance
        CUSTOM                 // Custom failure reason
    }

    private AbilityResult(
            boolean success,
            String message,
            FailureReason reason,
            @Nullable SequenceBasedSuccessChance successChance,
            @Nullable SanityPenalty sanityPenalty
    ) {
        this.success = success;
        this.message = message;
        this.failureReason = reason;
        this.successChance = successChance;
        this.sanityPenalty = sanityPenalty;
    }

    // ==========================================
    // Factory Methods - Success
    // ==========================================

    public static AbilityResult success() {
        return new AbilityResult(true, null, FailureReason.NONE, null, null);
    }

    public static AbilityResult successWithMessage(String message) {
        return new AbilityResult(true, message, FailureReason.NONE, null, null);
    }

    /**
     * Success with sanity penalty - ability executed successfully but sanity loss caused a penalty
     */
    public static AbilityResult successWithPenalty(SanityPenalty penalty, String message) {
        return new AbilityResult(true, message, FailureReason.NONE, null, penalty);
    }

    // ==========================================
    // Factory Methods - Failure
    // ==========================================

    public static AbilityResult failure(String reason) {
        return new AbilityResult(false, reason, FailureReason.CUSTOM, null, null);
    }

    public static AbilityResult cooldownFailure(long remainingSeconds) {
        String message = "Cooldown: " + remainingSeconds + "с";
        return new AbilityResult(false, message, FailureReason.COOLDOWN, null, null);
    }

    public static AbilityResult insufficientResources(String message) {
        return new AbilityResult(false, message, FailureReason.INSUFFICIENT_RESOURCES, null, null);
    }

    public static AbilityResult invalidTarget(String message) {
        return new AbilityResult(false, message, FailureReason.INVALID_TARGET, null, null);
    }

    /**
     * Create failure due to sequence-based resistance
     */
    public static AbilityResult sequenceResistance(SequenceBasedSuccessChance successChance) {
        String message = String.format(
                "Ціль чинила опір! (Шанс успіху: %s)",
                successChance.getFormattedChance()
        );
        return new AbilityResult(false, message, FailureReason.SEQUENCE_RESISTANCE, successChance, null);
    }

    // ==========================================
    // Getters
    // ==========================================

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    @Nullable
    public SequenceBasedSuccessChance getSuccessChance() {
        return successChance;
    }

    @Nullable
    public SanityPenalty getSanityPenalty() {
        return sanityPenalty;
    }

    // ==========================================
    // Convenience Methods
    // ==========================================

    public boolean isSequenceResistance() {
        return failureReason == FailureReason.SEQUENCE_RESISTANCE;
    }

    public boolean isCooldownFailure() {
        return failureReason == FailureReason.COOLDOWN;
    }

    public boolean hasMessage() {
        return message != null && !message.isEmpty();
    }

    /**
     * Check if result has sanity penalty (success or failure with penalty)
     */
    public boolean hasSanityPenalty() {
        return sanityPenalty != null && sanityPenalty.hasEffect();
    }

    @Override
    public String toString() {
        return "AbilityResult[" +
                "success=" + success + ", " +
                "message=" + message + ", " +
                "reason=" + failureReason + ", " +
                "successChance=" + (successChance != null ? successChance.getFormattedChance() : "N/A") + ", " +
                "sanityPenalty=" + (sanityPenalty != null ? sanityPenalty : "N/A") +
                ']';
    }
}