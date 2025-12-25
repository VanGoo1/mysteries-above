package me.vangoo.domain.services;

import me.vangoo.domain.valueobjects.Sequence;

public class MasteryProgressionCalculator {
    private static final int[] SPIRITUALITY_PER_MASTERY = {
            500,  // Sequence 0 - Demigod (hardest) - 50,000 total
            400,  // Sequence 1 - Angel - 40,000 total
            350,  // Sequence 2 - Saint - 35,000 total
            300,  // Sequence 3 - Demi-Saint - 30,000 total
            250,  // Sequence 4 - High-Sequence - 25,000 total
            200,  // Sequence 5 - Mid-Sequence - 20,000 total
            150,  // Sequence 6 - 15,000 total
            100,  // Sequence 7 - 10,000 total
            75,   // Sequence 8 - 7,500 total
            50    // Sequence 9 - Starter (easiest) - 5,000 total
    };

    public static int getSpiritualityPerMastery(Sequence sequence) {
        int level = Math.min(9, Math.max(0, sequence.level()));
        return SPIRITUALITY_PER_MASTERY[level];
    }

    public static int calculateMasteryGain(int spiritualitySpent, Sequence sequence) {
        if (spiritualitySpent <= 0) {
            return 0;
        }

        int costPerPoint = getSpiritualityPerMastery(sequence);

        // Each mastery point requires a fixed amount of spirituality
        // Example: If cost is 50 and spent is 120, gain = 2 mastery
        return spiritualitySpent / costPerPoint;

    }

    public static int getTotalSpiritualityForFullMastery(Sequence sequence) {
        return getSpiritualityPerMastery(sequence) * 100;
    }
}
