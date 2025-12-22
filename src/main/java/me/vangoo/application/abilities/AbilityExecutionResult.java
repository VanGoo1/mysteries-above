package me.vangoo.application.abilities;

import javax.annotation.Nullable;
import java.util.Objects;

public final class AbilityExecutionResult {
    private final boolean success;
    private final String message;
    @Nullable
    private final SanityLossCheckResult sanityCheck;

    public AbilityExecutionResult(
            boolean success,
            String message,
            @Nullable SanityLossCheckResult sanityCheck
    ) {
        this.success = success;
        this.message = message;
        this.sanityCheck = sanityCheck;
    }

    public boolean isSuccess() {
        return success;
    }

    public static AbilityExecutionResult success() {
        return new AbilityExecutionResult(true, null, null);
    }

    public static AbilityExecutionResult successWithSanityCheck(SanityLossCheckResult sanityCheck) {
        return new AbilityExecutionResult(true, null, sanityCheck);
    }

    public static AbilityExecutionResult failure(String reason) {
        return new AbilityExecutionResult(false, reason, null);
    }

    public static AbilityExecutionResult failureWithSanityCheck(
            String reason,
            SanityLossCheckResult sanityCheck
    ) {
        return new AbilityExecutionResult(false, reason, sanityCheck);
    }

    public boolean hasSanityPenalty() {
        return sanityCheck != null && sanityCheck.penalty().hasEffect();
    }

    public String message() {
        return message;
    }

    @Nullable
    public SanityLossCheckResult sanityCheck() {
        return sanityCheck;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (AbilityExecutionResult) obj;
        return this.success == that.success &&
                Objects.equals(this.message, that.message) &&
                Objects.equals(this.sanityCheck, that.sanityCheck);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, sanityCheck);
    }

    @Override
    public String toString() {
        return "AbilityExecutionResult[" +
                "success=" + success + ", " +
                "message=" + message + ", " +
                "sanityCheck=" + sanityCheck + ']';
    }

}