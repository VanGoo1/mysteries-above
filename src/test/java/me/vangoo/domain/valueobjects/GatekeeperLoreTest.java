package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чиста математика Воротаря: тіло (М1), Крок Воротаря (М2), Двері DRAG/PURGE/BIND/DESCEND
 * (М4–М6), Внутрішній Загробний Світ (М7), стеля почту (М8), четверо нових духів (М9).
 */
class GatekeeperLoreTest {

    private static final Sequence SEQ_5 = Sequence.of(5);
    private static final Sequence SEQ_0 = Sequence.of(0);

    @Test
    void bodyIsStrongerThanTheSpiritGuideBeforeIt() {
        assertEquals(10, GatekeeperLore.PHYSIQUE_HP_BASE);
        assertTrue(GatekeeperLore.PHYSIQUE_HP_BASE > SpiritGuideLore.PHYSIQUE_HP_BASE,
                "тіло воротаря міцніше за провідникове");
    }

    @Test
    void stepBaseValuesAreDefinedForSequenceFive() {
        assertEquals(20, GatekeeperLore.STEP_COST);
        assertEquals(8, GatekeeperLore.stepCooldownSeconds(SEQ_5));
        assertEquals(8.0, GatekeeperLore.stepDistance(SEQ_5), 1e-9);
        assertEquals(5, GatekeeperLore.stepImpactDamage(SEQ_5));
    }

    @Test
    void stepCooldownShrinksButNeverBelowFloor() {
        for (int level = 0; level <= 5; level++) {
            int cooldown = GatekeeperLore.stepCooldownSeconds(Sequence.of(level));
            assertTrue(cooldown >= 4 && cooldown <= 8, "кулдаун Кроку поза межами на Seq " + level);
        }
        assertTrue(GatekeeperLore.stepCooldownSeconds(SEQ_0) < GatekeeperLore.stepCooldownSeconds(SEQ_5));
    }

    @Test
    void stepDistanceGrowsTowardsSequenceZeroButStaysUnderTheCap() {
        assertTrue(GatekeeperLore.stepDistance(SEQ_0) > GatekeeperLore.stepDistance(SEQ_5));
        for (int level = 0; level <= 5; level++) {
            assertTrue(GatekeeperLore.stepDistance(Sequence.of(level)) <= 12.0);
        }
    }

    @Test
    void doorBaseValuesAreDefinedForSequenceFive() {
        assertEquals(70, GatekeeperLore.DOOR_COST);
        assertEquals(60, GatekeeperLore.doorCooldownSeconds(SEQ_5));
        assertEquals(20.0, GatekeeperLore.doorPlacementRange(SEQ_5), 1e-9);
        assertEquals(8.0, GatekeeperLore.dragGateAoeRadius(SEQ_5), 1e-9);
        assertEquals(8, GatekeeperLore.dragDamage(SEQ_5));
        assertEquals(0.25, GatekeeperLore.DRAG_EXECUTE_HP_FRACTION, 1e-9);
    }

    @Test
    void doorCooldownShrinksButNeverBelowFloor() {
        for (int level = 0; level <= 5; level++) {
            int cooldown = GatekeeperLore.doorCooldownSeconds(Sequence.of(level));
            assertTrue(cooldown >= 30 && cooldown <= 60, "кулдаун Дверей поза межами на Seq " + level);
        }
        assertTrue(GatekeeperLore.doorCooldownSeconds(SEQ_0) < GatekeeperLore.doorCooldownSeconds(SEQ_5));
    }

    @Test
    void dragRangeAndRadiusGrowTowardsSequenceZeroButStayUnderTheirCaps() {
        assertTrue(GatekeeperLore.doorPlacementRange(SEQ_0) > GatekeeperLore.doorPlacementRange(SEQ_5));
        assertTrue(GatekeeperLore.dragGateAoeRadius(SEQ_0) > GatekeeperLore.dragGateAoeRadius(SEQ_5));
        for (int level = 0; level <= 5; level++) {
            assertTrue(GatekeeperLore.doorPlacementRange(Sequence.of(level)) <= 32.0);
            assertTrue(GatekeeperLore.dragGateAoeRadius(Sequence.of(level)) <= 14.0);
        }
    }

    @Test
    void purgeAndBindBaseValuesAreDefinedForSequenceFive() {
        assertEquals(10.0, GatekeeperLore.purgeRadius(SEQ_5), 1e-9);
        assertEquals(6.0, GatekeeperLore.bindRadius(SEQ_5), 1e-9);
        assertEquals(4, GatekeeperLore.BIND_DURATION_SECONDS);
        assertEquals(4, GatekeeperLore.BIND_SLOWNESS_AMPLIFIER);
    }

    @Test
    void purgeAndBindRadiiGrowTowardsSequenceZeroButStayUnderTheirCaps() {
        assertTrue(GatekeeperLore.purgeRadius(SEQ_0) > GatekeeperLore.purgeRadius(SEQ_5));
        assertTrue(GatekeeperLore.bindRadius(SEQ_0) > GatekeeperLore.bindRadius(SEQ_5));
        for (int level = 0; level <= 5; level++) {
            assertTrue(GatekeeperLore.purgeRadius(Sequence.of(level)) <= 18.0);
            assertTrue(GatekeeperLore.bindRadius(Sequence.of(level)) <= 11.0);
        }
    }

    @Test
    void descendBaseValuesAreDefinedForSequenceFive() {
        assertEquals(12.0, GatekeeperLore.descendRadius(SEQ_5), 1e-9);
        assertEquals(12, GatekeeperLore.descendDurationSeconds(SEQ_5));
        assertEquals(0.20, GatekeeperLore.DESCEND_UNDEAD_DAMAGE_BONUS, 1e-9);
        assertEquals(1.0, GatekeeperLore.DESCEND_OWNER_HEAL_PER_SECOND, 1e-9);
    }

    @Test
    void descendRadiusAndDurationGrowTowardsSequenceZeroButStayUnderTheirCaps() {
        assertTrue(GatekeeperLore.descendRadius(SEQ_0) > GatekeeperLore.descendRadius(SEQ_5));
        assertTrue(GatekeeperLore.descendDurationSeconds(SEQ_0) > GatekeeperLore.descendDurationSeconds(SEQ_5));
        for (int level = 0; level <= 5; level++) {
            assertTrue(GatekeeperLore.descendRadius(Sequence.of(level)) <= 20.0);
            assertTrue(GatekeeperLore.descendDurationSeconds(Sequence.of(level)) <= 20);
        }
    }

    @Test
    void underworldCastBaseCostMatchesTheCheapestOccupant() {
        assertEquals(30, GatekeeperLore.UNDERWORLD_CAST_BASE_COST);
        assertEquals(30, GatekeeperLore.WANDERING_SPIRIT_CAST_COST);
        assertEquals(30, GatekeeperLore.RESURRECTED_SERVANT_CAST_COST);
        assertTrue(GatekeeperLore.UNDERWORLD_CAST_BASE_COST <= GatekeeperLore.DEATH_KNIGHT_CAST_COST);
        assertTrue(GatekeeperLore.UNDERWORLD_CAST_BASE_COST <= GatekeeperLore.SHADOW_PYTHON_CAST_COST);
        assertTrue(GatekeeperLore.UNDERWORLD_CAST_BASE_COST <= GatekeeperLore.LIVING_SHADOW_CAST_COST);
        assertTrue(GatekeeperLore.UNDERWORLD_CAST_BASE_COST <= GatekeeperLore.LAKE_GODDESS_CAST_COST);
    }

    @Test
    void underworldCastCooldownShrinksButNeverBelowFloor() {
        for (int level = 0; level <= 5; level++) {
            int cooldown = GatekeeperLore.underworldCastCooldownSeconds(Sequence.of(level));
            assertTrue(cooldown >= 15 && cooldown <= 30, "кулдаун ВЗС поза межами на Seq " + level);
        }
        assertTrue(GatekeeperLore.underworldCastCooldownSeconds(SEQ_0)
                < GatekeeperLore.underworldCastCooldownSeconds(SEQ_5));
    }

    @Test
    void erosionSpansTwoToEightAcrossOccupantsByStrength() {
        assertEquals(2.0, GatekeeperLore.WANDERING_SPIRIT_EROSION_HP, 1e-9, "найслабший окупант — найменша ерозія");
        assertEquals(8.0, GatekeeperLore.LAKE_GODDESS_EROSION_HP, 1e-9, "найдорожчий окупант — найбільша ерозія");
        for (double erosion : new double[]{
                GatekeeperLore.DEATH_KNIGHT_EROSION_HP, GatekeeperLore.SHADOW_PYTHON_EROSION_HP,
                GatekeeperLore.LIVING_SHADOW_EROSION_HP, GatekeeperLore.LAKE_GODDESS_EROSION_HP,
                GatekeeperLore.WANDERING_SPIRIT_EROSION_HP, GatekeeperLore.RESURRECTED_SERVANT_EROSION_HP}) {
            assertTrue(erosion >= 2.0 && erosion <= 8.0, "ерозія поза заявленим діапазоном −2…−8");
        }
    }

    @Test
    void retinueCapStepsUpExactlyAtSequenceFive() {
        assertEquals(10, SpiritGuideLore.RETINUE_CAP, "база Посл. 6 лишається незмінною");
        assertEquals(10, GatekeeperLore.retinueCap(Sequence.of(6)), "Посл. 6 усе ще зупиняється на 10");
        assertEquals(10, GatekeeperLore.retinueCap(Sequence.of(9)), "слабші тіри теж на 10");
        assertEquals(12, GatekeeperLore.retinueCap(SEQ_5), "Посл. 5 — «slightly increased» до 12");
        assertEquals(12, GatekeeperLore.retinueCap(SEQ_0), "стеля не росте далі за Посл. 5 у цьому плані");
    }

    @Test
    void fourNewSpiritsHaveDistinctRecruitCosts() {
        assertTrue(GatekeeperLore.PUS_OF_MAN_RECRUIT_COST < GatekeeperLore.DEATH_ENVOY_RECRUIT_COST,
                "пасивний дух дешевший за бойового");
        assertTrue(GatekeeperLore.PALE_GIRL_RECRUIT_COST < GatekeeperLore.SEA_BEASTS_RECRUIT_COST,
                "пасивний дух дешевший за бойового");
        assertEquals(GatekeeperLore.PUS_OF_MAN_RECRUIT_COST, GatekeeperLore.PALE_GIRL_RECRUIT_COST,
                "обидва пасивні духи коштують однаково");
    }

    @Test
    void deathEnvoyAndSeaBeastsAreActiveOccupantsWhilePusAndPaleAreFree() {
        assertEquals(60, GatekeeperLore.DEATH_ENVOY_CAST_COST);
        assertEquals(55, GatekeeperLore.SEA_BEASTS_CAST_COST);
        assertEquals(0, GatekeeperLore.PUS_OF_MAN_CAST_COST, "пасивний окупант не кастується");
        assertEquals(0, GatekeeperLore.PALE_GIRL_CAST_COST, "пасивний окупант не кастується");
    }

    @Test
    void pusOfManWardHasAFiveMinuteCooldown() {
        assertEquals(300, GatekeeperLore.PUS_OF_MAN_WARD_COOLDOWN_SECONDS);
        assertEquals(5, GatekeeperLore.PUS_OF_MAN_INVISIBILITY_SECONDS);
        assertEquals(10, GatekeeperLore.PUS_OF_MAN_WEAKNESS_SECONDS);
    }
}
