package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MembershipTest {

    private static final int[] THRESHOLDS = {0, 200, 600, 1500, 3500};

    @Test
    void contributionGrowsBothCounters() {
        Membership m = new Membership(UUID.randomUUID(), "church-evernight");
        m.addContribution(250);
        assertEquals(250, m.lifetimeContribution());
        assertEquals(250, m.balance());
        assertEquals(ChurchRank.SLUZHKA, m.rank(THRESHOLDS));
    }

    @Test
    void spendingLowersBalanceButNotRank() {
        Membership m = new Membership(UUID.randomUUID(), "church-evernight");
        m.addContribution(700);
        assertTrue(m.spend(650));
        assertEquals(50, m.balance());
        assertEquals(700, m.lifetimeContribution());
        assertEquals(ChurchRank.DYAKON, m.rank(THRESHOLDS)); // ранг за lifetime, не за балансом
        assertFalse(m.spend(51)); // бракує — відмова, баланс не змінюється
        assertEquals(50, m.balance());
    }

    @Test
    void rejectsNonPositiveAmounts() {
        Membership m = new Membership(UUID.randomUUID(), "church-evernight");
        assertThrows(IllegalArgumentException.class, () -> m.addContribution(0));
        assertThrows(IllegalArgumentException.class, () -> m.spend(-1));
    }
}
