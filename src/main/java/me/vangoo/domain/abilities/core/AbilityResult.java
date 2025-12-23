package me.vangoo.domain.abilities.core;

import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;

import javax.annotation.Nullable;

public class AbilityResult {
    private final boolean success;
    private final String message;
    private final FailureReason failureReason;
    private final SequenceBasedSuccessChance successChance;

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
            @Nullable SequenceBasedSuccessChance successChance
    ) {
        this.success = success;
        this.message = message;
        this.failureReason = reason;
        this.successChance = successChance;
    }

    // ==========================================
    // Factory Methods - Success
    // ==========================================

    public static AbilityResult success() {
        return new AbilityResult(true, null, FailureReason.NONE, null);
    }

    public static AbilityResult successWithMessage(String message) {
        return new AbilityResult(true, message, FailureReason.NONE, null);
    }

    // ==========================================
    // Factory Methods - Failure
    // ==========================================

    public static AbilityResult failure(String reason) {
        return new AbilityResult(false, reason, FailureReason.CUSTOM, null);
    }

    public static AbilityResult cooldownFailure(long remainingSeconds) {
        String message = "Cooldown: " + remainingSeconds + "с";
        return new AbilityResult(false, message, FailureReason.COOLDOWN, null);
    }

    public static AbilityResult insufficientResources(String message) {
        return new AbilityResult(false, message, FailureReason.INSUFFICIENT_RESOURCES, null);
    }

    public static AbilityResult invalidTarget(String message) {
        return new AbilityResult(false, message, FailureReason.INVALID_TARGET, null);
    }

    /**
     * Create failure due to sequence-based resistance
     *
     * @param successChance The chance calculation that was rolled
     * @return Failure result with sequence information
     */
    public static AbilityResult sequenceResistance(SequenceBasedSuccessChance successChance) {
        String message = String.format(
                "Ціль чинила опір! (Шанс успіху: %s)",
                successChance.getFormattedChance()
        );
        return new AbilityResult(false, message, FailureReason.SEQUENCE_RESISTANCE, successChance);
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

    @Override
    public String toString() {
        return "AbilityResult[" +
                "success=" + success + ", " +
                "message=" + message + ", " +
                "reason=" + failureReason + ", " +
                "successChance=" + (successChance != null ? successChance.getFormattedChance() : "N/A") +
                ']';
    }
}