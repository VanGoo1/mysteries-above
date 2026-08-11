package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чиста математика Могильника: базові значення Посл. 8, напрям росту, стелі й підлоги.
 * Посл. 9 тут не перевіряється — Могильник ніколи не буває слабшим за 8.
 */
class GravediggerLoreTest {

    private static final Sequence SEQ_8 = Sequence.of(8);
    private static final Sequence SEQ_0 = Sequence.of(0);

    @Test
    void baseValuesAreDefinedForSequenceEight() {
        assertEquals(15, GravediggerLore.spiritCooldownSeconds(SEQ_8));
        assertEquals(2, GravediggerLore.spiritLimit(SEQ_8));
        assertEquals(16.0, GravediggerLore.spiritRecruitRadius(SEQ_8), 1e-9);
        assertEquals(10, GravediggerLore.graspSeconds(SEQ_8));
        assertEquals(14, GravediggerLore.eyeCooldownSeconds(SEQ_8));
        assertEquals(24.0, GravediggerLore.eyeRange(SEQ_8), 1e-9);
        assertEquals(2.2, GravediggerLore.eyeDeadDamageMultiplier(SEQ_8), 1e-9);
        assertEquals(1.6, GravediggerLore.eyeLivingDamageMultiplier(SEQ_8), 1e-9);
    }

    @Test
    void everyScaledValueGrowsMonotonicallyTowardsSequenceZero() {
        for (int level = 8; level > 0; level--) {
            Sequence weaker = Sequence.of(level);
            Sequence stronger = Sequence.of(level - 1);

            assertTrue(GravediggerLore.spiritLimit(stronger)
                            >= GravediggerLore.spiritLimit(weaker),
                    "почет просів на Seq " + (level - 1));
            assertTrue(GravediggerLore.spiritRecruitRadius(stronger)
                            >= GravediggerLore.spiritRecruitRadius(weaker),
                    "радіус вербування просів на Seq " + (level - 1));
            assertTrue(GravediggerLore.graspSeconds(stronger)
                            >= GravediggerLore.graspSeconds(weaker),
                    "тривалість хвату просіла на Seq " + (level - 1));
            assertTrue(GravediggerLore.eyeRange(stronger)
                            >= GravediggerLore.eyeRange(weaker),
                    "дальність Ока просіла на Seq " + (level - 1));
            assertTrue(GravediggerLore.eyeDeadDamageMultiplier(stronger)
                            >= GravediggerLore.eyeDeadDamageMultiplier(weaker),
                    "множник вузла просів на Seq " + (level - 1));
        }
    }

    @Test
    void cooldownsShrinkWithPowerButNeverBelowTheirFloors() {
        for (int level = 0; level <= 8; level++) {
            Sequence sequence = Sequence.of(level);
            int spirit = GravediggerLore.spiritCooldownSeconds(sequence);
            int eye = GravediggerLore.eyeCooldownSeconds(sequence);
            assertTrue(spirit >= 5 && spirit <= 15, "кулдаун почту поза межами на Seq " + level);
            assertTrue(eye >= 5 && eye <= 14, "кулдаун Ока поза межами на Seq " + level);
        }
        assertTrue(GravediggerLore.spiritCooldownSeconds(SEQ_0)
                < GravediggerLore.spiritCooldownSeconds(SEQ_8));
        assertTrue(GravediggerLore.eyeCooldownSeconds(SEQ_0)
                < GravediggerLore.eyeCooldownSeconds(SEQ_8));
    }

    @Test
    void rangesLimitsAndDamageStayUnderTheirCaps() {
        for (int level = 0; level <= 8; level++) {
            Sequence sequence = Sequence.of(level);
            assertTrue(GravediggerLore.spiritRecruitRadius(sequence) <= 48.0);
            assertTrue(GravediggerLore.eyeRange(sequence) <= 64.0);
            assertTrue(GravediggerLore.spiritLimit(sequence) <= 6);
            assertTrue(GravediggerLore.spiritLimit(sequence) >= 2,
                    "почет ніколи не має бути меншим за базові два");
            assertTrue(GravediggerLore.eyeDeadDamageMultiplier(sequence) <= 3.5);
            assertTrue(GravediggerLore.eyeLivingDamageMultiplier(sequence) >= 1.0,
                    "вузол ніколи не має послаблювати урон");
        }
    }

    @Test
    void livingTargetAlwaysGetsExactlyHalfTheDeadBonus() {
        for (int level = 0; level <= 8; level++) {
            Sequence sequence = Sequence.of(level);
            double deadBonus = GravediggerLore.eyeDeadDamageMultiplier(sequence) - 1.0;
            double livingBonus = GravediggerLore.eyeLivingDamageMultiplier(sequence) - 1.0;
            assertEquals(deadBonus / 2.0, livingBonus, 1e-9,
                    "живе має брати рівно половину надбавки на Seq " + level);
            assertTrue(livingBonus < deadBonus, "живе не може бути вигіднішим за мертве");
        }
    }

    @Test
    void fixedCostsAndGraspConstantsMatchTheApprovedDesign() {
        assertEquals(6, GravediggerLore.PHYSIQUE_HP_BASE);
        assertEquals(25, GravediggerLore.SPIRIT_SUMMON_COST);
        assertEquals(12, GravediggerLore.SPIRIT_RECRUIT_COST);
        assertTrue(GravediggerLore.SPIRIT_RECRUIT_COST < GravediggerLore.SPIRIT_SUMMON_COST,
                "вербувати сусіда мусить бути дешевше, ніж кликати зі Світу Духів");
        assertEquals(20, GravediggerLore.EYE_COST);
        assertEquals(1, GravediggerLore.MAX_CONCURRENT_NODES);
        assertEquals(3, GravediggerLore.GRASP_SLOWNESS_AMPLIFIER);
        assertEquals(3.0, GravediggerLore.GRASP_REACH, 1e-9);
        assertTrue(GravediggerLore.GRASP_REACH < GravediggerLore.SPIRIT_TELEPORT_DISTANCE,
                "тримати можна лише впритул, а не через півкарти");
        assertTrue(GravediggerLore.SPIRIT_FOLLOW_DISTANCE < GravediggerLore.SPIRIT_TELEPORT_DISTANCE);
        assertEquals(1800, GravediggerLore.NODE_TTL_SECONDS);
    }
}
