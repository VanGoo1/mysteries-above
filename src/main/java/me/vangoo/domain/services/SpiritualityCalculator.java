package me.vangoo.domain.services;

import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.Sequence;

public class SpiritualityCalculator {
    private static final int[] MIN_VALUES = {100, 300, 600, 1000, 1600, 3000, 6000, 10000, 12000, 14000};
    private static final int[] MAX_VALUES = {200, 500, 900, 1500, 2000, 5000, 8000, 11000, 13000, 15000};

    // Regeneration base rates per sequence (points per second)
    // Sequence 0 = fastest, Sequence 9 = slowest
    private static final int[] REGEN_RATES = {
            100,  // Sequence 0
            40,   // Sequence 1
            20,   // Sequence 2
            15,   // Sequence 3
            12,   // Sequence 4
            6,   // Sequence 5
            4,   // Sequence 6
            3,   // Sequence 7
            2,   // Sequence 8
            1    // Sequence 9
    };

    /**
     * Calculate maximum spirituality based on sequence and mastery
     */
    public int calculateMaximumSpirituality(Sequence sequence, Mastery mastery) {
        if (sequence.level() < 0) {
            return 0;
        }

        int seq = Math.min(9, sequence.level());
        int idx = 9 - seq;

        int min = MIN_VALUES[idx];
        int max = MAX_VALUES[idx];

        // Ensure max is not less than min
        if (max < min) {
            max = min;
        }

        // Interpolate between min and max based on mastery percentage
        double t = mastery.value() / 100.0;

        return (int) Math.round(min + (max - min) * t);
    }

    public int calculateRegenerationRate(Sequence sequence, Mastery mastery) {
        int seq = Math.min(9, Math.max(0, sequence.level()));
        int base = REGEN_RATES[seq];

        double masteryFactor = Math.min(100, Math.max(0, mastery.value())) / 100.0;
        double multiplier = 1.0 + masteryFactor * 0.5;
        int regen = (int) Math.round(base * multiplier);

        return Math.max(1, regen);
    }
}
