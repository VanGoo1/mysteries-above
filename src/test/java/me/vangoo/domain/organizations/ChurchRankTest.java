package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChurchRankTest {

    private static final int[] THRESHOLDS = {0, 200, 600, 1500, 3500};

    @Test
    void ranksResolveByLifetimeContribution() {
        assertEquals(ChurchRank.VIRIANYN, ChurchRank.of(0, THRESHOLDS));
        assertEquals(ChurchRank.VIRIANYN, ChurchRank.of(199, THRESHOLDS));
        assertEquals(ChurchRank.SLUZHKA, ChurchRank.of(200, THRESHOLDS));
        assertEquals(ChurchRank.DYAKON, ChurchRank.of(600, THRESHOLDS));
        assertEquals(ChurchRank.YEPYSKOP, ChurchRank.of(1500, THRESHOLDS));
        assertEquals(ChurchRank.KARDYNAL, ChurchRank.of(9999, THRESHOLDS));
    }

    @Test
    void rankGatesOrderableSequence() {
        assertEquals(8, ChurchRank.VIRIANYN.minOrderSequence());
        assertEquals(0, ChurchRank.KARDYNAL.minOrderSequence());
        // Стеля = max(ранг, PARTIAL-ліміт): Кардинал у церкві з доступом «до 3» — лише до 3.
        assertEquals(3, ChurchRank.KARDYNAL.lowestOrderableSequence(
                PathwayAccess.partial("Fool", 3)));
        // Вірянин із повним доступом — усе одно лише до 8.
        assertEquals(8, ChurchRank.VIRIANYN.lowestOrderableSequence(
                PathwayAccess.full("Door")));
    }
}
