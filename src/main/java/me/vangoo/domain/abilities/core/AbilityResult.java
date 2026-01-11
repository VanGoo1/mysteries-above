package me.vangoo.domain.abilities.core;

import me.vangoo.domain.valueobjects.SanityPenalty;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;

import javax.annotation.Nullable;

public class AbilityResult {
    private final boolean success;
    private final String message;
    private final FailureReason failureReason;
    private final SequenceBasedSuccessChance successChance;
    private final SanityPenalty sanityPenalty;
    private final boolean deferred;

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
            @Nullable SanityPenalty sanityPenalty,
            boolean deferred
    ) {
        this.success = success;
        this.message = message;
        this.failureReason = reason;
        this.successChance = successChance;
        this.sanityPenalty = sanityPenalty;
        this.deferred = deferred;
    }

    // ==========================================
    // Factory Methods - Success
    // ==========================================

    public static AbilityResult success() {
        return new AbilityResult(true, null, FailureReason.NONE, null, null, false);
    }

    public static AbilityResult successWithMessage(String message) {
        return new AbilityResult(true, message, FailureReason.NONE, null, null, false);
    }

    /**
     * Success with sanity penalty - ability executed successfully but sanity loss caused a penalty
     */
    public static AbilityResult successWithPenalty(SanityPenalty penalty, String message) {
        return new AbilityResult(true, message, FailureReason.NONE, null, penalty, false);
    }

    /**
     * Deferred execution - ability started but execution is deferred (e.g., waiting for user input).
     * Spirituality and cooldown should NOT be consumed yet.
     */
    public static AbilityResult deferred(String message) {
        return new AbilityResult(true, message, FailureReason.NONE, null, null, true);
    }

    /**
     * Deferred execution with no message.
     */
    public static AbilityResult deferred() {
        return new AbilityResult(true, null, FailureReason.NONE, null, null, true);
    }

    // ==========================================
    // Factory Methods - Failure
    // ==========================================

    public static AbilityResult failure(String reason) {
        return new AbilityResult(false, reason, FailureReason.CUSTOM, null, null, false);
    }

    public static AbilityResult cooldownFailure(long remainingSeconds) {
        String message = "Cooldown: " + remainingSeconds + "с";
        return new AbilityResult(false, message, FailureReason.COOLDOWN, null, null, false);
    }

    public static AbilityResult insufficientResources(String message) {
        return new AbilityResult(false, message, FailureReason.INSUFFICIENT_RESOURCES, null, null, false);
    }

    public static AbilityResult invalidTarget(String message) {
        return new AbilityResult(false, message, FailureReason.INVALID_TARGET, null, null, false);
    }

    /**
     * Create failure due to sequence-based resistance
     */
    public static AbilityResult sequenceResistance(SequenceBasedSuccessChance successChance) {
        String message = String.format(
                "Ціль чинила опір! (Шанс успіху: %s)",
                successChance.getFormattedChance()
        );
        return new AbilityResult(false, message, FailureReason.SEQUENCE_RESISTANCE, successChance, null, false);
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

    /**
     * Check if execution is deferred (spirituality should not be consumed yet).
     */
    public boolean isDeferred() {
        return deferred;
    }

    @Override
    public String toString() {
        return "AbilityResult[" +
                "success=" + success + ", " +
                "message=" + message + ", " +
                "reason=" + failureReason + ", " +
                "successChance=" + (successChance != null ? successChance.getFormattedChance() : "N/A") + ", " +
                "sanityPenalty=" + (sanityPenalty != null ? sanityPenalty : "N/A") + ", " +
                "deferred=" + deferred +
                ']';
    }
}