package me.vangoo.domain.valueobjects;

public record RampageState(
        RampagePhase phase,
        long startTimeMillis,
        int durationSeconds
) {

    public enum RampagePhase {
        NONE,           // Not in rampage
        BUILDING_UP,    // Warning phase (0-10s)
        CRITICAL,       // Critical phase (10-20s)
        TRANSFORMING    // Point of no return
    }

    private static final int WARNING_THRESHOLD = 10; // seconds

    public static RampageState none() {
        return new RampageState(RampagePhase.NONE, 0, 0);
    }

    public static RampageState start(int durationSeconds) {
        return new RampageState(
                RampagePhase.BUILDING_UP,
                System.currentTimeMillis(),
                durationSeconds
        );
    }

    /**
     * Check if rampage is active
     */
    public boolean isActive() {
        return phase != RampagePhase.NONE;
    }

    /**
     * Get elapsed time in seconds
     */
    public int getElapsedSeconds() {
        if (!isActive()) return 0;

        long elapsed = System.currentTimeMillis() - startTimeMillis;
        return (int) (elapsed / 1000);
    }

    /**
     * Get remaining time in seconds
     */
    public int getRemainingSeconds() {
        if (!isActive()) return 0;

        int remaining = durationSeconds - getElapsedSeconds();
        return Math.max(0, remaining);
    }

    /**
     * Get progress percentage (0.0 to 1.0)
     */
    public double getProgress() {
        if (!isActive()) return 0.0;

        int elapsed = getElapsedSeconds();
        return Math.min(1.0, (double) elapsed / durationSeconds);
    }

    /**
     * Update phase based on elapsed time
     */
    public RampageState updatePhase() {
        if (!isActive()) return this;

        int elapsed = getElapsedSeconds();

        if (elapsed >= durationSeconds) {
            return new RampageState(RampagePhase.TRANSFORMING, startTimeMillis, durationSeconds);
        } else if (elapsed >= WARNING_THRESHOLD) {
            return new RampageState(RampagePhase.CRITICAL, startTimeMillis, durationSeconds);
        } else {
            return new RampageState(RampagePhase.BUILDING_UP, startTimeMillis, durationSeconds);
        }
    }

    /**
     * Check if transformation should happen
     */
    public boolean shouldTransform() {
        return phase == RampagePhase.TRANSFORMING || getElapsedSeconds() >= durationSeconds;
    }

    /**
     * Check if in critical phase (last 10 seconds)
     */
    public boolean isCritical() {
        return phase == RampagePhase.CRITICAL || phase == RampagePhase.TRANSFORMING;
    }

    /**
     * Cancel rampage (rescue)
     */
    public RampageState cancel() {
        return none();
    }
}