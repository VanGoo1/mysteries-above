package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import static me.vangoo.domain.market.GatheringPhase.*;
import static org.junit.jupiter.api.Assertions.*;

class GatheringPhaseTest {

    @Test
    void allowsHappyPathCycle() {
        assertTrue(IDLE.canTransitionTo(ANNOUNCED));
        assertTrue(ANNOUNCED.canTransitionTo(OPEN));
        assertTrue(OPEN.canTransitionTo(CLOSING));
        assertTrue(CLOSING.canTransitionTo(IDLE));
    }

    @Test
    void allowsCancellingAnnouncementWhenNobodyJoined() {
        assertTrue(ANNOUNCED.canTransitionTo(IDLE));
    }

    @Test
    void rejectsSkippingPhases() {
        assertFalse(IDLE.canTransitionTo(OPEN));
        assertFalse(IDLE.canTransitionTo(CLOSING));
        assertFalse(ANNOUNCED.canTransitionTo(CLOSING));
        assertFalse(OPEN.canTransitionTo(IDLE));
        assertFalse(OPEN.canTransitionTo(ANNOUNCED));
        assertFalse(CLOSING.canTransitionTo(OPEN));
    }
}
