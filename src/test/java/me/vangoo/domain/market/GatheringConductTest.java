package me.vangoo.domain.market;

import me.vangoo.domain.market.GatheringConduct.Sanction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatheringConductTest {

    private final GatheringConduct conduct = new GatheringConduct();

    @Test
    void firstViolationWarnsThenSubsequentKick() {
        UUID p = UUID.randomUUID();
        assertEquals(Sanction.WARN, conduct.recordViolation(p));
        assertEquals(Sanction.KICK, conduct.recordViolation(p));
        assertEquals(Sanction.KICK, conduct.recordViolation(p));
    }

    @Test
    void countersAreIndependentPerPlayer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(Sanction.WARN, conduct.recordViolation(a));
        assertEquals(Sanction.WARN, conduct.recordViolation(b));
        assertEquals(Sanction.KICK, conduct.recordViolation(a));
    }

    @Test
    void resetClearsCounters() {
        UUID p = UUID.randomUUID();
        conduct.recordViolation(p);
        conduct.reset();
        assertEquals(Sanction.WARN, conduct.recordViolation(p));
    }
}
