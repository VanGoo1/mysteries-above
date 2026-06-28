package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnDistanceGateTest {

    @Test
    void insideRadiusIsNotFarEnough() {
        assertFalse(SpawnDistanceGate.isFarEnough(1000, 0, 2000));
        assertFalse(SpawnDistanceGate.isFarEnough(1000, 1000, 2000)); // ~1414 < 2000
    }

    @Test
    void onOrBeyondRadiusIsFarEnough() {
        assertTrue(SpawnDistanceGate.isFarEnough(2000, 0, 2000));
        assertTrue(SpawnDistanceGate.isFarEnough(0, 2500, 2000));
        assertTrue(SpawnDistanceGate.isFarEnough(1500, 1500, 2000)); // ~2121 >= 2000
    }

    @Test
    void zeroMinDistanceAlwaysFarEnough() {
        assertTrue(SpawnDistanceGate.isFarEnough(0, 0, 0));
        assertTrue(SpawnDistanceGate.isFarEnough(0, 0, -1));
    }
}
