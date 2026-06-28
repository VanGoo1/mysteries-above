package me.vangoo.domain.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequenceScalerTest {

    @Test
    void creatureDamageReductionWeakAtSeq9IsZero() {
        assertEquals(0.0, SequenceScaler.creatureDamageReduction(9), 1e-9);
    }

    @Test
    void creatureDamageReductionGrowsFivePercentPerLevel() {
        assertEquals(0.20, SequenceScaler.creatureDamageReduction(5), 1e-9); // power 4 * 0.05
        assertEquals(0.45, SequenceScaler.creatureDamageReduction(0), 1e-9); // power 9 * 0.05 = 0.45 (cap)
    }

    @Test
    void creatureDamageReductionCappedAt45Percent() {
        // even if an out-of-range stronger level were passed, never exceed the cap
        assertEquals(0.45, SequenceScaler.creatureDamageReduction(-1), 1e-9);
    }
}
