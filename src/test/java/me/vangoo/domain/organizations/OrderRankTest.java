package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderRankTest {

    @Test
    void rankDerivesFromSequenceBands() {
        assertEquals(OrderRank.PAWN, OrderRank.of(9));
        assertEquals(OrderRank.PAWN, OrderRank.of(8));
        assertEquals(OrderRank.BLADE, OrderRank.of(7));
        assertEquals(OrderRank.BLADE, OrderRank.of(6));
        assertEquals(OrderRank.TRUSTED, OrderRank.of(5));
        assertEquals(OrderRank.TRUSTED, OrderRank.of(4));
        assertEquals(OrderRank.MAGISTER, OrderRank.of(3));
        assertEquals(OrderRank.MAGISTER, OrderRank.of(2));
        assertEquals(OrderRank.HIDDEN_LORD, OrderRank.of(1));
        assertEquals(OrderRank.HIDDEN_LORD, OrderRank.of(0));
    }

    @Test
    void atLeastComparesLadderPosition() {
        assertTrue(OrderRank.TRUSTED.atLeast(OrderRank.PAWN));
        assertTrue(OrderRank.TRUSTED.atLeast(OrderRank.TRUSTED));
        assertFalse(OrderRank.PAWN.atLeast(OrderRank.TRUSTED));
    }

    @Test
    void weightAtLeastComparesSeverity() {
        assertTrue(TaskWeight.MAJOR.atLeast(TaskWeight.STANDARD));
        assertFalse(TaskWeight.LIGHT.atLeast(TaskWeight.STANDARD));
    }

    @Test
    void outOfRangeSequenceClamps() {
        assertEquals(OrderRank.PAWN, OrderRank.of(12));
        assertEquals(OrderRank.HIDDEN_LORD, OrderRank.of(-1));
    }
}
