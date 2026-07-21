package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Чиста математика Маріонетника (Seq 5): тайминги фаз і радіус бачення ниток. */
class FoolSeq5ValueObjectsTest {

    @Test
    void phase1ShrinksWithMasteryBetweenFiveAndTwentySeconds() {
        assertEquals(20 * 20, MarionetteTiming.phase1LockTicks(0), "0% засвоєння → 20с");
        assertEquals(5 * 20, MarionetteTiming.phase1LockTicks(100), "100% засвоєння → 5с");
        int mid = MarionetteTiming.phase1LockTicks(50);
        assertTrue(mid < MarionetteTiming.phase1LockTicks(0) && mid > MarionetteTiming.phase1LockTicks(100));
    }

    @Test
    void phase1ClampsOutOfRangeMastery() {
        assertEquals(MarionetteTiming.phase1LockTicks(0), MarionetteTiming.phase1LockTicks(-10));
        assertEquals(MarionetteTiming.phase1LockTicks(100), MarionetteTiming.phase1LockTicks(150));
    }

    @Test
    void playerConvertIsFiveMinutesAtSeq9AndFasterWhenStronger() {
        assertEquals(6000, MarionetteTiming.playerConvertTicks(Sequence.of(9)));
        assertTrue(MarionetteTiming.playerConvertTicks(Sequence.of(5))
                < MarionetteTiming.playerConvertTicks(Sequence.of(9)));
    }

    @Test
    void mobSwapIsAroundFifteenSecondsAndScales() {
        assertEquals(300, MarionetteTiming.mobSwapTicks(Sequence.of(9)));
        assertTrue(MarionetteTiming.mobSwapTicks(Sequence.of(5))
                < MarionetteTiming.mobSwapTicks(Sequence.of(9)));
        // Моб перетворюється значно швидше за гравця.
        assertTrue(MarionetteTiming.mobSwapTicks(Sequence.of(5))
                < MarionetteTiming.playerConvertTicks(Sequence.of(5)));
    }

    @Test
    void threadVisionRangeGrowsWithPowerAndIsCapped() {
        assertTrue(ThreadVisionRange.rangeFor(Sequence.of(5)) >= ThreadVisionRange.rangeFor(Sequence.of(9)));
        assertTrue(ThreadVisionRange.rangeFor(Sequence.of(0)) <= 40);
    }
}
