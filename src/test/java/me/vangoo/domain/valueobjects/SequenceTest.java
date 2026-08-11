package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceTest {

    @Test
    void demoteWeakensByOneLevel() {
        assertEquals(7, Sequence.of(6).demote().level());
    }

    @Test
    void cannotDemotePastWeakestSequence() {
        Sequence weakest = Sequence.of(9);
        assertFalse(weakest.canDemote());
        assertThrows(IllegalStateException.class, weakest::demote);
    }

    @Test
    void canDemoteFromAnyNonWeakestLevel() {
        assertTrue(Sequence.of(0).canDemote());
        assertTrue(Sequence.of(8).canDemote());
    }
}
