package me.vangoo.application.abilities;

public record SanityPenalty(PenaltyType type, int amount) {

    public enum PenaltyType {
        NONE,           // No penalty
        DAMAGE,         // Physical damage
        SPIRITUALITY_LOSS, // Spirituality deduction
        EXTREME         // Death + warden spawn
    }

    public static SanityPenalty none() {
        return new SanityPenalty(PenaltyType.NONE, 0);
    }

    public static SanityPenalty damage(int amount) {
        return new SanityPenalty(PenaltyType.DAMAGE, amount);
    }

    public static SanityPenalty spiritualityLoss(int amount) {
        return new SanityPenalty(PenaltyType.SPIRITUALITY_LOSS, amount);
    }

    public static SanityPenalty extreme() {
        return new SanityPenalty(PenaltyType.EXTREME, 0);
    }

    public boolean hasEffect() {
        return type != PenaltyType.NONE;
    }
}
