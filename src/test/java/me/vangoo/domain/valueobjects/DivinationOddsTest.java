package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє чисту математику шансу гадання без сервера — те, заради чого її винесли з ефектів.
 */
class DivinationOddsTest {

    private static final double EPS = 1e-4;

    @Test
    void equalSequenceIsCertain() {
        // Однаковий Sequence: база 100%, штраф 0 → 100%.
        assertEquals(1.0, new DivinationOdds(5, 5).successProbability(), EPS);
    }

    @Test
    void strongerCasterTakesOnlySmallPenalty() {
        // Кастер сильніший на 2 (5 проти 7): база 1.0, штраф min(0.2, 0.06)=0.06 → 0.94.
        assertEquals(0.94, new DivinationOdds(5, 7).successProbability(), EPS);
    }

    @Test
    void advantagedPenaltyIsCappedSoNeverBelowEightyPercent() {
        // Навіть за максимальної різниці штраф обмежений 0.2 → 1.0 * 0.8 = 0.8.
        assertEquals(0.8, new DivinationOdds(0, 9).successProbability(), EPS);
    }

    @Test
    void weakerCasterByOneDropsSharply() {
        // Кастер слабший на 1 (8 проти 7): база 0.3667, динаміка 0.65 → ≈0.2383.
        assertEquals(0.2383, new DivinationOdds(8, 7).successProbability(), EPS);
    }

    @Test
    void weakerCasterByLargeMarginIsNearlyHopeless() {
        // Кастер слабший на 4 (9 проти 5): база 0.10, множник 0.05 → 0.005.
        assertEquals(0.005, new DivinationOdds(9, 5).successProbability(), EPS);
    }

    @Test
    void probabilityAlwaysStaysWithinUnitInterval() {
        for (int caster = 0; caster <= 9; caster++) {
            for (int target = 0; target <= 9; target++) {
                double p = new DivinationOdds(caster, target).successProbability();
                assertTrue(p >= 0.0 && p <= 1.0,
                        "поза [0,1]: caster=" + caster + " target=" + target + " p=" + p);
            }
        }
    }

    @Test
    void formattedPercentMatchesProbability() {
        assertEquals("94%", new DivinationOdds(5, 7).formattedPercent());
    }
}
