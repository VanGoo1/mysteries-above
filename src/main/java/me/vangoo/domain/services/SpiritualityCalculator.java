package me.vangoo.domain.services;

import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.Sequence;

public class SpiritualityCalculator {
    private static final int[] MIN_VALUES = {100, 300, 600, 1000, 1600, 2280, 3100, 4049, 5126, 6331};
    private static final int[] MAX_VALUES = {200, 500, 900, 1500, 2000, 2700, 3460, 4306, 5237, 7000};

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
        float t = mastery.value() / 100.0f;

        return Math.round(min + (max - min) * t);
    }

    public int calculateRegenerationRate(Sequence sequence) {
        // Base regeneration of 1 per second
        // Could be enhanced based on sequence level
        return 1;
    }
}
