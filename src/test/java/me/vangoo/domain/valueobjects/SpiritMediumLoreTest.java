package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чиста математика Медіума Духів: базові значення Посл. 7, напрям росту, стелі й підлоги.
 * Слабші Послідовності тут не перевіряються — медіум ніколи не буває слабшим за 7.
 */
class SpiritMediumLoreTest {

    private static final Sequence SEQ_7 = Sequence.of(7);
    private static final Sequence SEQ_0 = Sequence.of(0);

    @Test
    void baseValuesAreDefinedForSequenceSeven() {
        assertEquals(24.0, SpiritMediumLore.perceptionRadius(SEQ_7), 1e-9);
        assertEquals(45, SpiritMediumLore.illusoryEyeCooldownSeconds(SEQ_7));
        assertEquals(15, SpiritMediumLore.illusoryEyeDurationSeconds(SEQ_7));
        assertEquals(48.0, SpiritMediumLore.illusoryEyeRadius(SEQ_7), 1e-9);
        assertEquals(90, SpiritMediumLore.frostCooldownSeconds(SEQ_7));
        assertEquals(12, SpiritMediumLore.frostDurationSeconds(SEQ_7));
        assertEquals(10.0, SpiritMediumLore.frostRadius(SEQ_7), 1e-9);
        assertEquals(1.0, SpiritMediumLore.frostTickDamage(SEQ_7), 1e-9);
        assertEquals(1.4, SpiritMediumLore.scytheDamageMultiplier(SEQ_7), 1e-9);
        assertEquals(60, SpiritMediumLore.earthCooldownSeconds(SEQ_7));
        assertEquals(5, SpiritMediumLore.earthHoldSeconds(SEQ_7));
        assertEquals(12.0, SpiritMediumLore.earthRange(SEQ_7), 1e-9);
        assertEquals(120, SpiritMediumLore.voiceDeadCooldownSeconds(SEQ_7));
        assertEquals(8.0, SpiritMediumLore.voiceDeadRadius(SEQ_7), 1e-9);
        assertEquals(60, SpiritMediumLore.voiceLivingCooldownSeconds(SEQ_7));
        assertEquals(16.0, SpiritMediumLore.voiceLivingRange(SEQ_7), 1e-9);
    }

    @Test
    void everyScaledValueGrowsMonotonicallyTowardsSequenceZero() {
        for (int level = 7; level > 0; level--) {
            Sequence weaker = Sequence.of(level);
            Sequence stronger = Sequence.of(level - 1);
            String where = " просів на Seq " + (level - 1);

            assertTrue(SpiritMediumLore.perceptionRadius(stronger)
                    >= SpiritMediumLore.perceptionRadius(weaker), "радіус сприйняття" + where);
            assertTrue(SpiritMediumLore.illusoryEyeDurationSeconds(stronger)
                    >= SpiritMediumLore.illusoryEyeDurationSeconds(weaker), "строк ока" + where);
            assertTrue(SpiritMediumLore.illusoryEyeRadius(stronger)
                    >= SpiritMediumLore.illusoryEyeRadius(weaker), "радіус ока" + where);
            assertTrue(SpiritMediumLore.frostDurationSeconds(stronger)
                    >= SpiritMediumLore.frostDurationSeconds(weaker), "строк домену" + where);
            assertTrue(SpiritMediumLore.frostRadius(stronger)
                    >= SpiritMediumLore.frostRadius(weaker), "радіус домену" + where);
            assertTrue(SpiritMediumLore.frostTickDamage(stronger)
                    >= SpiritMediumLore.frostTickDamage(weaker), "урон холоду" + where);
            assertTrue(SpiritMediumLore.scytheDamageMultiplier(stronger)
                    >= SpiritMediumLore.scytheDamageMultiplier(weaker), "множник коси" + where);
            assertTrue(SpiritMediumLore.earthHoldSeconds(stronger)
                    >= SpiritMediumLore.earthHoldSeconds(weaker), "хват землі" + where);
            assertTrue(SpiritMediumLore.earthRange(stronger)
                    >= SpiritMediumLore.earthRange(weaker), "дальність землі" + where);
            assertTrue(SpiritMediumLore.voiceDeadRadius(stronger)
                    >= SpiritMediumLore.voiceDeadRadius(weaker), "радіус душ" + where);
            assertTrue(SpiritMediumLore.voiceLivingRange(stronger)
                    >= SpiritMediumLore.voiceLivingRange(weaker), "дальність допиту" + where);
        }
    }

    @Test
    void cooldownsShrinkWithPowerButNeverBelowTheirFloors() {
        for (int level = 0; level <= 7; level++) {
            Sequence sequence = Sequence.of(level);
            int eye = SpiritMediumLore.illusoryEyeCooldownSeconds(sequence);
            int frost = SpiritMediumLore.frostCooldownSeconds(sequence);
            int earth = SpiritMediumLore.earthCooldownSeconds(sequence);
            int dead = SpiritMediumLore.voiceDeadCooldownSeconds(sequence);
            int living = SpiritMediumLore.voiceLivingCooldownSeconds(sequence);

            assertTrue(eye >= 15 && eye <= 45, "кулдаун ока поза межами на Seq " + level);
            assertTrue(frost >= 30 && frost <= 90, "кулдаун тіні поза межами на Seq " + level);
            assertTrue(earth >= 20 && earth <= 60, "кулдаун землі поза межами на Seq " + level);
            assertTrue(dead >= 40 && dead <= 120, "кулдаун мертвих поза межами на Seq " + level);
            assertTrue(living >= 20 && living <= 60, "кулдаун живих поза межами на Seq " + level);
        }
        assertTrue(SpiritMediumLore.frostCooldownSeconds(SEQ_0)
                < SpiritMediumLore.frostCooldownSeconds(SEQ_7));
        assertTrue(SpiritMediumLore.voiceDeadCooldownSeconds(SEQ_0)
                < SpiritMediumLore.voiceDeadCooldownSeconds(SEQ_7));
    }

    @Test
    void rangesAndDamageStayUnderTheirCaps() {
        for (int level = 0; level <= 7; level++) {
            Sequence sequence = Sequence.of(level);
            assertTrue(SpiritMediumLore.perceptionRadius(sequence) <= 48.0);
            assertTrue(SpiritMediumLore.illusoryEyeRadius(sequence) <= 64.0);
            assertTrue(SpiritMediumLore.frostRadius(sequence) <= 20.0,
                    "домен не має перерости вікіних 20 метрів");
            assertTrue(SpiritMediumLore.frostTickDamage(sequence) <= 3.0);
            assertTrue(SpiritMediumLore.scytheDamageMultiplier(sequence) <= 2.5);
            assertTrue(SpiritMediumLore.scytheDamageMultiplier(sequence) >= 1.0,
                    "коса ніколи не має послаблювати удар");
            assertTrue(SpiritMediumLore.earthRange(sequence) <= 24.0);
            assertTrue(SpiritMediumLore.voiceDeadRadius(sequence) <= 16.0);
            assertTrue(SpiritMediumLore.voiceLivingRange(sequence) <= 32.0);
        }
    }

    @Test
    void onlyAStrongerTargetCostsSanityAndTheCostIsCapped() {
        assertEquals(0, SpiritMediumLore.voiceBacklashSanity(SEQ_7, Sequence.of(9)),
                "слабша ціль відкату не дає");
        assertEquals(0, SpiritMediumLore.voiceBacklashSanity(SEQ_7, SEQ_7),
                "рівна ціль відкату не дає");
        assertEquals(8, SpiritMediumLore.voiceBacklashSanity(SEQ_7, Sequence.of(6)));
        assertEquals(16, SpiritMediumLore.voiceBacklashSanity(SEQ_7, Sequence.of(5)));
        assertEquals(40, SpiritMediumLore.voiceBacklashSanity(SEQ_7, SEQ_0),
                "відкат не перевищує стелі 40");
    }

    @Test
    void fixedCostsMatchTheApprovedDesign() {
        assertEquals(7, SpiritMediumLore.PHYSIQUE_HP_BASE);
        assertTrue(SpiritMediumLore.PHYSIQUE_HP_BASE > GravediggerLore.PHYSIQUE_HP_BASE,
                "тіло медіума міцніше за могильникове, хай і трохи");
        assertEquals(30, SpiritMediumLore.ILLUSORY_EYE_COST);
        assertEquals(55, SpiritMediumLore.FROST_SHADOW_COST);
        assertEquals(40, SpiritMediumLore.EARTH_SPIRIT_COST);
        assertEquals(4, SpiritMediumLore.EARTH_SPIRIT_BLOOD_HP);
        assertEquals(35, SpiritMediumLore.VOICE_COST);
        assertEquals(20, SpiritMediumLore.GUISE_ACTIVATION_COST);
        assertEquals(2, SpiritMediumLore.GUISE_PERIODIC_COST);
        assertEquals(10, SpiritMediumLore.GUISE_COOLDOWN_SECONDS);
        assertEquals(1800, SpiritMediumLore.SOUL_TTL_SECONDS);
    }
}
