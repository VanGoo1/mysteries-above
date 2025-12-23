package me.vangoo.domain.valueobjects;

public record SanityLoss(int scale) {
    private static final int MIN_VALUE = 0;
    private static final int MAX_VALUE = 100;

    public SanityLoss {
        if (scale < MIN_VALUE || scale > MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Sanity loss scale must be between " + MIN_VALUE + " and " + MAX_VALUE + ", got: " + scale
            );
        }
    }

    public static SanityLoss of(int scale) {
        return new SanityLoss(scale);
    }

    public static SanityLoss none() {
        return new SanityLoss(MIN_VALUE);
    }

    public static SanityLoss maximum() {
        return new SanityLoss(MAX_VALUE);
    }

    public SanityLoss increase(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot increase by negative amount: " + amount);
        }
        int newScale = Math.min(scale + amount, MAX_VALUE);
        return new SanityLoss(newScale);
    }

    public SanityLoss decrease(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot decrease by negative amount: " + amount);
        }
        int newScale = Math.max(scale - amount, MIN_VALUE);
        return new SanityLoss(newScale);
    }

    public SanityLoss reset() {
        return none();
    }

    /**
     * Calculate failure chance based on sanity loss scale
     * Updated with more balanced progression
     */
    public double calculateFailureChance() {
        if (scale <= 10) return 0.0;           // 0-10: No failure (Negligible)
        else if (scale <= 20) return 0.05;     // 11-20: 5% (Minor)
        else if (scale <= 40) return 0.15;     // 21-40: 15% (Moderate)
        else if (scale <= 60) return 0.35;     // 41-60: 35% (Serious)
        else if (scale <= 80) return 0.60;     // 61-80: 60% (Severe)
        else if (scale <= 95) return 0.85;     // 81-95: 85% (Critical)
        else return 1.0;                       // 96-100: 100% (Extreme - always fails)
    }

    public boolean isNegligible() {
        return scale <= 10;
    }

    public boolean isMinor() {
        return scale > 10 && scale <= 20;
    }

    public boolean isModerate() {
        return scale > 20 && scale <= 40;
    }

    public boolean isSerious() {
        return scale > 40 && scale <= 60;
    }

    public boolean isSevere() {
        return scale > 60 && scale <= 80;
    }

    public boolean isCritical() {
        return scale > 80 && scale <= 95;
    }

    public boolean isExtreme() {
        return scale > 95;
    }

    @Override
    public String toString() {
        return scale + "/100";
    }
}