package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskQuotaTest {

    private static final long DAY = 24 * 3_600_000L;

    private final TaskQuota quota = new TaskQuota(DAY, 5);

    @Test
    void windowIsOpenUntilExactlyOneWindowHasPassed() {
        long start = 1_000_000L;
        assertFalse(quota.windowExpired(start, start));
        assertFalse(quota.windowExpired(start, start + DAY - 1));
        assertTrue(quota.windowExpired(start, start + DAY));
    }

    @Test
    void quotaAllowsExactlySetsPerDayGenerations() {
        assertTrue(quota.canGenerate(0));
        assertTrue(quota.canGenerate(4));
        assertFalse(quota.canGenerate(5));
        assertFalse(quota.canGenerate(6));
    }

    @Test
    void setsLeftNeverGoesNegative() {
        assertEquals(5, quota.setsLeft(0));
        assertEquals(1, quota.setsLeft(4));
        assertEquals(0, quota.setsLeft(5));
        assertEquals(0, quota.setsLeft(99));
    }

    @Test
    void countdownShrinksAndFloorsAtZero() {
        long start = 5_000_000L;
        assertEquals(DAY, quota.millisUntilReset(start, start));
        assertEquals(DAY / 2, quota.millisUntilReset(start, start + DAY / 2));
        assertEquals(0L, quota.millisUntilReset(start, start + DAY));
        assertEquals(0L, quota.millisUntilReset(start, start + DAY * 3));
    }

    /** Переведений назад годинник не мусить замикати гравця у вікні назавжди. */
    @Test
    void windowStartInTheFutureCountsAsExpired() {
        long now = 1_000_000L;
        assertTrue(quota.windowExpired(now + DAY, now));
        assertEquals(0L, quota.millisUntilReset(now + DAY, now));
    }

    @Test
    void rejectsNonPositiveConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TaskQuota(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new TaskQuota(DAY, 0));
    }
}
