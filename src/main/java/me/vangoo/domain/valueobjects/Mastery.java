package me.vangoo.domain.valueobjects;

public record Mastery(int value) {
    private static final int MIN_VALUE = 0;
    private static final int MAX_VALUE = 100;

    public Mastery {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Mastery must be between " + MIN_VALUE + " and " + MAX_VALUE + ", got: " + value
            );
        }
    }

    public static Mastery of(int value) {
        return new Mastery(value);
    }

    public static Mastery zero() {
        return new Mastery(MIN_VALUE);
    }

    public static Mastery max() {
        return new Mastery(MAX_VALUE);
    }

    public Mastery increment(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot increment by negative amount: " + amount);
        }
        int newValue = Math.min(value + amount, MAX_VALUE);
        return new Mastery(newValue);
    }

    public Mastery increment() {
        return increment(1);
    }

    public Mastery reset() {
        return zero();
    }

    public boolean canAdvance() {
        return value >= MAX_VALUE;
    }

    public int getPercentage() {
        return value;
    }

    @Override
    public String toString() {
        return value + "%";
    }
}
