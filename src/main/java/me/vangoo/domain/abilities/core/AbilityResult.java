package me.vangoo.domain.abilities.core;

public class AbilityResult {
    private final boolean success;
    private final String message;

    private AbilityResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AbilityResult success() {
        return new AbilityResult(true, null);
    }

    public static AbilityResult failure(String reason) {
        return new AbilityResult(false, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
