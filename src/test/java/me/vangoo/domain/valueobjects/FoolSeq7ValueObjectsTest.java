package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Чиста математика нових VO Фокусника (Seq 7): дальність стрибка, глибина
 * дихання, партія ляльок, зцілення, паперова зброя, ілюзії.
 */
class FoolSeq7ValueObjectsTest {

    @Test
    void flameJumpRangeGrowsWithPowerAndIsCapped() {
        assertEquals(FlameJumpRange.BASE_RANGE, FlameJumpRange.rangeFor(Sequence.of(9)));
        assertTrue(FlameJumpRange.rangeFor(Sequence.of(5)) > FlameJumpRange.rangeFor(Sequence.of(9)));
        assertTrue(FlameJumpRange.rangeFor(Sequence.of(0)) <= 60);
    }

    @Test
    void breathDepthGrowsWithPower() {
        assertEquals(BreathDepthLimit.BASE_DEPTH, BreathDepthLimit.maxDepth(Sequence.of(9)));
        assertTrue(BreathDepthLimit.maxDepth(Sequence.of(5)) >= BreathDepthLimit.maxDepth(Sequence.of(9)));
        assertTrue(BreathDepthLimit.maxDepth(Sequence.of(0)) <= 12);
    }

    @Test
    void dollBatchScalesFromThreeToSixWithCostAndCap() {
        assertEquals(3, DollBatch.dollsPerCast(Sequence.of(9)));
        assertEquals(6, DollBatch.dollsPerCast(Sequence.of(5)));
        assertEquals(6, DollBatch.dollsPerCast(Sequence.of(0)), "стеля 6 ляльок за каст");
        assertEquals(3 * DollBatch.PAPER_PER_DOLL, DollBatch.paperCost(Sequence.of(9)));
        assertTrue(DollBatch.maxStored(Sequence.of(9)) > DollBatch.dollsPerCast(Sequence.of(9)));
    }

    @Test
    void recentDamageHealIsHalfAndCapped() {
        // 20 отриманої шкоди → 10, але стеля Seq9 = 8.
        assertEquals(8.0, RecentDamageHeal.healAmount(20, Sequence.of(9)), 1e-9);
        // 10 шкоди → 5 (нижче стелі).
        assertEquals(5.0, RecentDamageHeal.healAmount(10, Sequence.of(9)), 1e-9);
        assertEquals(0.0, RecentDamageHeal.healAmount(0, Sequence.of(9)), 1e-9);
        assertTrue(RecentDamageHeal.healCeiling(Sequence.of(5)) > RecentDamageHeal.healCeiling(Sequence.of(9)));
    }

    @Test
    void paperWeaponCostsAreInRange() {
        for (PaperWeaponType type : PaperWeaponType.values()) {
            assertTrue(type.paperCost() >= 32 && type.paperCost() <= 64,
                    "вартість поза 32..64: " + type);
            assertTrue(type.uses() > 0);
            assertNotNull(type.displayName());
        }
    }

    @Test
    void illusionKindsHavePositiveCost() {
        for (IllusionKind kind : IllusionKind.values()) {
            assertTrue(kind.spiritualityCost() > 0);
            assertNotNull(kind.displayName());
        }
    }
}
