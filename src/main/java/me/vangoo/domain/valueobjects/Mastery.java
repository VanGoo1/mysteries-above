package me.vangoo.domain.valueobjects;

public record Mastery(double value) {
    private static final double MIN_VALUE = 0.0;
    private static final double MAX_VALUE = 100.0;

    public Mastery(double value) {
        // Округлюємо до 2 знаків після коми
        this.value = Math.round(value * 100.0) / 100.0;

        if (this.value < MIN_VALUE || this.value > MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Mastery must be between " + MIN_VALUE + " and " + MAX_VALUE + ", got: " + this.value
            );
        }
    }

    public static Mastery of(double value) {
        return new Mastery(value);
    }

    public static Mastery zero() {
        return new Mastery(MIN_VALUE);
    }

    public static Mastery max() {
        return new Mastery(MAX_VALUE);
    }

    public Mastery add(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot increment by negative amount: " + amount);
        }
        double newValue = Math.min(value + amount, MAX_VALUE);
        return new Mastery(newValue);
    }

    public Mastery increment() {
        return add(1.0);
    }

    public Mastery reset() {
        return zero();
    }

    public boolean canAdvance() {
        return value >= MAX_VALUE;
    }

    public double getPercentage() {
        return value;
    }

    public double getValue() {
        return value;
    }
    public int asInt() {
        return (int) value;
    }
    @Override
    public String toString() {
        return String.format("%.2f%%", value);
    }
}
