package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чиста математика Провідника духів: базові значення Посл. 6, напрям росту, стелі й підлоги.
 * Слабші Послідовності тут не перевіряються — провідник ніколи не буває слабшим за 6.
 */
class SpiritGuideLoreTest {

    private static final Sequence SEQ_6 = Sequence.of(6);
    private static final Sequence SEQ_0 = Sequence.of(0);

    @Test
    void baseValuesAreDefinedForSequenceSix() {
        assertEquals(70, SpiritGuideLore.languageCooldownSeconds(SEQ_6));
        assertEquals(5, SpiritGuideLore.languageDurationSeconds(SEQ_6));
        assertEquals(20.0, SpiritGuideLore.languageRange(SEQ_6), 1e-9);
        assertEquals(12, SpiritGuideLore.resurrectionCooldownSeconds(SEQ_6));
        assertEquals(45, SpiritGuideLore.subjugationCooldownSeconds(SEQ_6));
        assertEquals(12.0, SpiritGuideLore.subjugationRadius(SEQ_6), 1e-9);
        assertEquals(30, SpiritGuideLore.spiritSwapWardSeconds(SEQ_6));
        assertEquals(90, SpiritGuideLore.spiritSwapCooldownSeconds(SEQ_6));
    }

    @Test
    void everyScaledValueGrowsMonotonicallyTowardsSequenceZero() {
        for (int level = 6; level > 0; level--) {
            Sequence weaker = Sequence.of(level);
            Sequence stronger = Sequence.of(level - 1);
            String where = " просів на Seq " + (level - 1);

            assertTrue(SpiritGuideLore.languageDurationSeconds(stronger)
                    >= SpiritGuideLore.languageDurationSeconds(weaker), "строк душі" + where);
            assertTrue(SpiritGuideLore.languageRange(stronger)
                    >= SpiritGuideLore.languageRange(weaker), "дальність мови" + where);
            assertTrue(SpiritGuideLore.subjugationRadius(stronger)
                    >= SpiritGuideLore.subjugationRadius(weaker), "радіус підкорення" + where);
            assertTrue(SpiritGuideLore.spiritSwapWardSeconds(stronger)
                    >= SpiritGuideLore.spiritSwapWardSeconds(weaker), "щит обміну" + where);
        }
    }

    @Test
    void cooldownsShrinkWithPowerButNeverBelowTheirFloors() {
        for (int level = 0; level <= 6; level++) {
            Sequence sequence = Sequence.of(level);
            int language = SpiritGuideLore.languageCooldownSeconds(sequence);
            int resurrection = SpiritGuideLore.resurrectionCooldownSeconds(sequence);
            int subjugation = SpiritGuideLore.subjugationCooldownSeconds(sequence);
            int swap = SpiritGuideLore.spiritSwapCooldownSeconds(sequence);
            int pact = SpiritGuideLore.pactCooldownSeconds(sequence);

            assertTrue(language >= 30 && language <= 70, "кулдаун мови поза межами на Seq " + level);
            assertTrue(resurrection >= 5 && resurrection <= 12,
                    "кулдаун воскресіння поза межами на Seq " + level);
            assertTrue(subjugation >= 20 && subjugation <= 45,
                    "кулдаун підкорення поза межами на Seq " + level);
            assertTrue(swap >= 40 && swap <= 90, "кулдаун обміну поза межами на Seq " + level);
            assertTrue(pact >= 10 && pact <= 30, "кулдаун домовленості поза межами на Seq " + level);
        }
        assertTrue(SpiritGuideLore.languageCooldownSeconds(SEQ_0)
                < SpiritGuideLore.languageCooldownSeconds(SEQ_6));
        assertTrue(SpiritGuideLore.spiritSwapCooldownSeconds(SEQ_0)
                < SpiritGuideLore.spiritSwapCooldownSeconds(SEQ_6));
        assertTrue(SpiritGuideLore.pactCooldownSeconds(SEQ_0)
                < SpiritGuideLore.pactCooldownSeconds(SEQ_6));
    }

    @Test
    void rangesStayUnderTheirCaps() {
        for (int level = 0; level <= 6; level++) {
            Sequence sequence = Sequence.of(level);
            assertTrue(SpiritGuideLore.languageRange(sequence) <= 32.0);
            assertTrue(SpiritGuideLore.subjugationRadius(sequence) <= 24.0);
        }
    }

    @Test
    void onlyAStrongerRivalTurnsTheRetinueAndTheChanceIsCapped() {
        assertEquals(0.0, SpiritGuideLore.rebellionChancePerSecond(SEQ_6, Sequence.of(7)), 1e-9,
                "слабший суперник почту не зачіпає");
        assertEquals(0.0, SpiritGuideLore.rebellionChancePerSecond(SEQ_6, SEQ_6), 1e-9,
                "рівний суперник почту не зачіпає");
        assertEquals(0.05, SpiritGuideLore.rebellionChancePerSecond(SEQ_6, Sequence.of(5)), 1e-9);
        assertEquals(0.15, SpiritGuideLore.rebellionChancePerSecond(SEQ_6, Sequence.of(3)), 1e-9);
        assertEquals(0.30, SpiritGuideLore.rebellionChancePerSecond(SEQ_6, SEQ_0), 1e-9,
                "найбільший можливий розрив для Посл. 6 — шість рівнів");
    }

    @Test
    void fixedCostsMatchTheApprovedDesign() {
        assertEquals(8, SpiritGuideLore.PHYSIQUE_HP_BASE);
        assertTrue(SpiritGuideLore.PHYSIQUE_HP_BASE > SpiritMediumLore.PHYSIQUE_HP_BASE,
                "тіло провідника міцніше за медіумове");
        assertEquals(300, SpiritGuideLore.CORPSE_TTL_SECONDS);
        assertTrue(SpiritGuideLore.CORPSE_TTL_SECONDS < SpiritMediumLore.SOUL_TTL_SECONDS,
                "тіло псується швидше, ніж тримається душа");
        assertEquals(60, SpiritGuideLore.LANGUAGE_COST);
        assertEquals(35, SpiritGuideLore.RESURRECTION_COST);
        assertEquals(45, SpiritGuideLore.SUBJUGATION_COST);
        assertEquals(10, SpiritGuideLore.RETINUE_CAP);
        assertEquals(50, SpiritGuideLore.LIVING_SHADOW_COST);
        assertEquals(60, SpiritGuideLore.SHADOW_PYTHON_COST);
        assertEquals(70, SpiritGuideLore.DEATH_KNIGHT_COST);
        assertEquals(80, SpiritGuideLore.LAKE_GODDESS_COST);
        assertEquals(40, SpiritGuideLore.SPIRIT_SWAP_COST);
        assertEquals(1, SpiritGuideLore.SPIRIT_SWAP_SERVANT_COST);
        assertEquals(15, SpiritGuideLore.SOAR_COST);
        assertEquals(20, SpiritGuideLore.SOAR_COOLDOWN_SECONDS);
        assertEquals(24.0, SpiritGuideLore.REBELLION_RADIUS, 1e-9);
    }
}
