package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApexGateTest {

    @Test
    void commonCreaturesReachEveryone() {
        assertTrue(ApexGate.allows(CreatureTier.COMMON, null));
        assertTrue(ApexGate.allows(CreatureTier.COMMON, 9));
    }

    @Test
    void apexNeverReachesNonBeyonders() {
        assertFalse(ApexGate.allows(CreatureTier.APEX, null));
    }

    @Test
    void apexOnlyReachesPlayersNearItsOwnSequence() {
        for (int seq = 9; seq > ApexGate.APEX_MIN_SEQUENCE; seq--) {
            assertFalse(ApexGate.allows(CreatureTier.APEX, seq), "seq " + seq + " must not meet apex");
        }
        for (int seq = ApexGate.APEX_MIN_SEQUENCE; seq >= 0; seq--) {
            assertTrue(ApexGate.allows(CreatureTier.APEX, seq), "seq " + seq + " must meet apex");
        }
    }
}
