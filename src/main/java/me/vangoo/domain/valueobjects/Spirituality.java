package me.vangoo.domain.valueobjects;

public record Spirituality(int current, int maximum) {
    private static final int MIN_VALUE = 0;
    private static final double CRITICAL_THRESHOLD = 0.05;

    public Spirituality {
        if (maximum < MIN_VALUE) {
            throw new IllegalArgumentException("Maximum spirituality cannot be negative: " + maximum);
        }
        if (current < MIN_VALUE) {
            throw new IllegalArgumentException("Current spirituality cannot be negative: " + current);
        }
        if (current > maximum) {
            throw new IllegalArgumentException(
                    "Current spirituality (" + current + ") cannot exceed maximum (" + maximum + ")"
            );
        }
    }

    public static Spirituality of(int current, int maximum) {
        return new Spirituality(current, maximum);
    }

    public static Spirituality empty(int maximum) {
        return new Spirituality(MIN_VALUE, maximum);
    }

    public static Spirituality full(int maximum) {
        return new Spirituality(maximum, maximum);
    }

    public boolean hasSufficient(int required) {
        return current >= required;
    }

    public boolean isCritical() {
        if (maximum == 0) return false;
        return (double) current / maximum <= CRITICAL_THRESHOLD;
    }

    public Spirituality increment(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot increment by negative amount: " + amount);
        }
        int newCurrent = Math.min(current + amount, maximum);
        return new Spirituality(newCurrent, maximum);
    }

    public Spirituality decrement(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot decrement by negative amount: " + amount);
        }
        int newCurrent = Math.max(current - amount, MIN_VALUE);
        return new Spirituality(newCurrent, maximum);
    }

    public Spirituality withNewMaximum(int newMaximum) {
        int newCurrent = Math.min(current, newMaximum);
        return new Spirituality(newCurrent, newMaximum);
    }

    public Spirituality regenerate(int amount) {
        return increment(amount);
    }

    public boolean isFull() {
        return current == maximum;
    }

    public boolean isEmpty() {
        return current == MIN_VALUE;
    }

    public double getPercentage() {
        if (maximum == 0) return 0.0;
        return (double) current / maximum;
    }

    @Override
    public String toString() {
        return current + "/" + maximum;
    }
}