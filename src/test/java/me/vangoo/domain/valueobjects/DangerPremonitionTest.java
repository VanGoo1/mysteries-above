package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чиста математика передчуття небезпеки — монотонність за силою та межі.
 */
class DangerPremonitionTest {

    @Test
    void lethalDodgeChanceGrowsWithPowerAndIsCapped() {
        double weak = DangerPremonition.lethalDodgeChance(Sequence.of(9));
        double strong = DangerPremonition.lethalDodgeChance(Sequence.of(5));
        assertTrue(strong > weak, "нижча послідовність має давати вищий шанс");
        assertEquals(0.12, weak, 1e-9);
        // Найсильніша послідовність підходить до стелі, але не перевищує її.
        assertEquals(0.39, DangerPremonition.lethalDodgeChance(Sequence.of(0)), 1e-9);
    }

    @Test
    void lethalDodgeChanceNeverExceedsBounds() {
        for (int level = 0; level <= 9; level++) {
            double c = DangerPremonition.lethalDodgeChance(Sequence.of(level));
            assertTrue(c >= 0.0 && c <= 0.40, "шанс поза межами на Seq " + level);
        }
    }

    @Test
    void dodgeCooldownShrinksWithPowerButHasFloor() {
        long weak = DangerPremonition.lethalDodgeCooldownMillis(Sequence.of(9));
        long strong = DangerPremonition.lethalDodgeCooldownMillis(Sequence.of(5));
        assertEquals(20_000L, weak);
        assertTrue(strong < weak, "сильніший відновлює передчуття швидше");
        assertTrue(DangerPremonition.lethalDodgeCooldownMillis(Sequence.of(0)) >= 6_000L,
                "кулдаун не коротший за підлогу");
    }

    @Test
    void actionPredictionChanceGrowsWithPowerAndIsCapped() {
        double weak = DangerPremonition.actionPredictionChance(Sequence.of(9));
        double strong = DangerPremonition.actionPredictionChance(Sequence.of(5));
        assertTrue(strong > weak);
        assertEquals(0.04, weak, 1e-9);
        assertTrue(DangerPremonition.actionPredictionChance(Sequence.of(0)) <= 0.25);
    }
}
