package me.vangoo.domain.valueobjects;

import java.util.Objects;

public record Sequence(int level) {
    private static final int MIN_SEQUENCE = 0;
    private static final int MAX_SEQUENCE = 9;

    public Sequence {
        if (level < MIN_SEQUENCE || level > MAX_SEQUENCE) {
            throw new IllegalArgumentException(
                    "Sequence must be between " + MIN_SEQUENCE + " and " + MAX_SEQUENCE + ", got: " + level
            );
        }
    }

    public static Sequence of(int level) {
        return new Sequence(level);
    }

    public static Sequence starter() {
        return new Sequence(MAX_SEQUENCE);
    }

    public Sequence advance() {
        if (!canAdvance()) {
            throw new IllegalStateException("Cannot advance from sequence " + level);
        }
        return new Sequence(level - 1);
    }

    public boolean canAdvance() {
        return level > MIN_SEQUENCE;
    }

    public boolean isStarter() {
        return level == MAX_SEQUENCE;
    }

    public boolean isHighest() {
        return level == MIN_SEQUENCE;
    }

    public int[] getAllSequencesUpToCurrent() {
        int count = MAX_SEQUENCE - level + 1;
        int[] sequences = new int[count];
        for (int i = 0; i < count; i++) {
            sequences[i] = MAX_SEQUENCE - i;
        }
        return sequences;
    }

    @Override
    public String toString() {
        return "Sequence " + level;
    }
}
