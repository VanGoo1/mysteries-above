package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чиста математика Трупозбирача: базові значення Посл. 9, напрям росту, стелі й підлоги.
 */
class CorpseCollectorLoreTest {

    private static final Sequence SEQ_9 = Sequence.of(9);
    private static final Sequence SEQ_0 = Sequence.of(0);

    @Test
    void baseValuesAreDefinedForSequenceNine() {
        assertEquals(12, CorpseCollectorLore.knowledgeCooldownSeconds(SEQ_9));
        assertEquals(20.0, CorpseCollectorLore.markRange(SEQ_9), 1e-9);
        assertEquals(20, CorpseCollectorLore.markDurationSeconds(SEQ_9));
        assertEquals(1.35, CorpseCollectorLore.markDamageMultiplier(SEQ_9), 1e-9);
        assertEquals(10.0, CorpseCollectorLore.autopsyRadius(SEQ_9), 1e-9);
        assertEquals(1800, CorpseCollectorLore.autopsyWindowSeconds(SEQ_9));
        assertEquals(5, CorpseCollectorLore.autopsyMaxRecords(SEQ_9));
        assertEquals(20.0, CorpseCollectorLore.spiritVisionRange(SEQ_9), 1e-9);
        assertEquals(16.0, CorpseCollectorLore.deathSightRange(SEQ_9), 1e-9);
        assertEquals(24.0, CorpseCollectorLore.gloomRadius(SEQ_9), 1e-9);
        assertEquals(2.0, CorpseCollectorLore.decayAcceleration(SEQ_9), 1e-9);
    }

    @Test
    void everyScaledValueGrowsMonotonicallyTowardsSequenceZero() {
        for (int level = 9; level > 0; level--) {
            Sequence weaker = Sequence.of(level);
            Sequence stronger = Sequence.of(level - 1);

            assertTrue(CorpseCollectorLore.markRange(stronger)
                            >= CorpseCollectorLore.markRange(weaker),
                    "дальність мітки просіла на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.markDurationSeconds(stronger)
                            >= CorpseCollectorLore.markDurationSeconds(weaker),
                    "тривалість мітки просіла на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.markDamageMultiplier(stronger)
                            >= CorpseCollectorLore.markDamageMultiplier(weaker),
                    "множник урону просів на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.autopsyRadius(stronger)
                            >= CorpseCollectorLore.autopsyRadius(weaker),
                    "радіус розтину просів на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.autopsyWindowSeconds(stronger)
                            >= CorpseCollectorLore.autopsyWindowSeconds(weaker),
                    "вікно розтину просіло на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.autopsyMaxRecords(stronger)
                            >= CorpseCollectorLore.autopsyMaxRecords(weaker),
                    "кількість записів просіла на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.spiritVisionRange(stronger)
                            >= CorpseCollectorLore.spiritVisionRange(weaker),
                    "радіус зору просів на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.deathSightRange(stronger)
                            >= CorpseCollectorLore.deathSightRange(weaker),
                    "радіус бачення духів просів на Seq " + (level - 1));
            assertTrue(CorpseCollectorLore.gloomRadius(stronger)
                            >= CorpseCollectorLore.gloomRadius(weaker),
                    "радіус похмурої присутності просів на Seq " + (level - 1));
        }
    }

    @Test
    void cooldownShrinksWithPowerButNeverBelowTheFloor() {
        for (int level = 0; level <= 9; level++) {
            int cooldown = CorpseCollectorLore.knowledgeCooldownSeconds(Sequence.of(level));
            assertTrue(cooldown >= 4 && cooldown <= 12, "кулдаун поза межами на Seq " + level);
        }
        assertTrue(CorpseCollectorLore.knowledgeCooldownSeconds(SEQ_0)
                < CorpseCollectorLore.knowledgeCooldownSeconds(SEQ_9));
    }

    @Test
    void rangesAndDamageStayUnderTheirCaps() {
        for (int level = 0; level <= 9; level++) {
            Sequence sequence = Sequence.of(level);
            assertTrue(CorpseCollectorLore.markRange(sequence) <= 60.0);
            assertTrue(CorpseCollectorLore.spiritVisionRange(sequence) <= 60.0);
            assertTrue(CorpseCollectorLore.deathSightRange(sequence) <= 48.0);
            assertTrue(CorpseCollectorLore.gloomRadius(sequence) <= 64.0);
            assertTrue(CorpseCollectorLore.autopsyRadius(sequence) <= 40.0);
            assertTrue(CorpseCollectorLore.markDamageMultiplier(sequence) <= 2.5);
            assertTrue(CorpseCollectorLore.markDamageMultiplier(sequence) >= 1.0,
                    "мітка ніколи не має послаблювати урон");
        }
    }

    @Test
    void decayAccelerationTranslatesIntoExtraTicksBurnedPerSecond() {
        // Посл. 9: удвічі швидше = ще 20 тіків понад ванільну секунду.
        assertEquals(20, CorpseCollectorLore.decayExtraTicksPerSecond(SEQ_9));
        assertTrue(CorpseCollectorLore.decayExtraTicksPerSecond(SEQ_0)
                > CorpseCollectorLore.decayExtraTicksPerSecond(SEQ_9));
        for (int level = 0; level <= 9; level++) {
            assertTrue(CorpseCollectorLore.decayExtraTicksPerSecond(Sequence.of(level)) > 0,
                    "прискорення згасання не має зникати на Seq " + level);
        }
    }

    @Test
    void markSlotIsSingleAndPhysiqueMatchesOtherSequenceNineTiers() {
        assertEquals(1, CorpseCollectorLore.MAX_CONCURRENT_MARKS);
        assertEquals(3, CorpseCollectorLore.PHYSIQUE_HP_BASE);
        assertEquals(15, CorpseCollectorLore.KNOWLEDGE_COST);
        assertEquals(3, CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST);
        assertEquals(10, CorpseCollectorLore.PROVOKE_MEMORY_SECONDS);
    }
}
