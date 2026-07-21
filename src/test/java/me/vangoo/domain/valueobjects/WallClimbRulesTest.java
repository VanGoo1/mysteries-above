package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WallClimbRulesTest {

    @Test
    void climbSpeedGrowsWithPowerButIsCapped() {
        double weak = WallClimbRules.climbSpeed(Sequence.of(9));
        double strong = WallClimbRules.climbSpeed(Sequence.of(5));
        assertTrue(strong >= weak, "сильніший лізе не повільніше");
        for (int level = 0; level <= 9; level++) {
            double s = WallClimbRules.climbSpeed(Sequence.of(level));
            assertTrue(s > 0 && s <= 0.35, "швидкість поза межами на Seq " + level);
        }
    }
}
