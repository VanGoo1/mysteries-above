package me.vangoo.domain.valueobjects;

import java.util.Random;

/**
 * Value Object: Represents success chance based on sequence difference
 *
 * Rules from Lord of Mysteries universe:
 * - Same sequence: 100% success
 * - Higher attacking lower: 90-100% success
 * - Lower attacking higher: 10-50% success
 * - Large difference (3+): very hard/impossible for weaker
 */
public record SequenceBasedSuccessChance(int casterSequence, int targetSequence) {
    private static final Random RANDOM = new Random();

    // Configuration constants
    private static final double SAME_SEQUENCE_CHANCE = 1.0; // 100%
    private static final double LOWER_TO_HIGHER_MIN = 0.10; // 10%
    private static final double LOWER_TO_HIGHER_BASE = 0.50; // 50%
    private static final int LARGE_DIFFERENCE_THRESHOLD = 3;

    public SequenceBasedSuccessChance {
        if (casterSequence < 0 || casterSequence > 9) {
            throw new IllegalArgumentException("Caster sequence must be 0-9, got: " + casterSequence);
        }
        if (targetSequence < 0 || targetSequence > 9) {
            throw new IllegalArgumentException("Target sequence must be 0-9, got: " + targetSequence);
        }
    }

    /**
     * Calculate success chance percentage (0.0 to 1.0)
     *
     * @return Probability of success
     */
    public double calculateChance() {
        int difference = casterSequence - targetSequence;

        // Same sequence - always succeed
        if (difference == 0) {
            return SAME_SEQUENCE_CHANCE;
        }

        // Caster is weaker (higher sequence number)
        if (difference > 0) {
            return calculateWeakerAttackingStronger(difference);
        }

        // Caster is stronger (lower sequence number)
        return 1;
    }

    /**
     * Calculate chance when weaker attacks stronger
     * Lower chance with bigger difference
     */
    private double calculateWeakerAttackingStronger(int difference) {
        // difference is 1, 2, 3, 4, ...
        // Map to 50% - 10% range (decreasing)

        if (difference >= LARGE_DIFFERENCE_THRESHOLD) {
            return LOWER_TO_HIGHER_MIN; // 10% for 3+ difference
        }

        // Linear interpolation between 50% and 10%
        // diff=1: 50%, diff=2: 30%, diff=3: 10%
        double range = LOWER_TO_HIGHER_BASE - LOWER_TO_HIGHER_MIN;
        double step = range / LARGE_DIFFERENCE_THRESHOLD;

        return LOWER_TO_HIGHER_BASE - (step * difference);
    }

    /**
     * Roll for success based on calculated chance
     *
     * @return true if successful, false if failed
     */
    public boolean rollSuccess() {
        double chance = calculateChance();
        return RANDOM.nextDouble() < chance;
    }

    /**
     * Get formatted chance percentage for display
     *
     * @return Percentage string (e.g., "85%")
     */
    public String getFormattedChance() {
        return String.format("%.0f%%", calculateChance() * 100);
    }

    /**
     * Check if this represents an advantageous situation for caster
     *
     * @return true if caster is stronger or equal
     */
    public boolean isCasterAdvantaged() {
        return casterSequence <= targetSequence;
    }

    /**
     * Get the absolute sequence difference
     */
    public int getSequenceDifference() {
        return Math.abs(casterSequence - targetSequence);
    }

    /**
     * Check if difference is considered "large" (3+)
     */
    public boolean isLargeDifference() {
        return getSequenceDifference() >= LARGE_DIFFERENCE_THRESHOLD;
    }
}