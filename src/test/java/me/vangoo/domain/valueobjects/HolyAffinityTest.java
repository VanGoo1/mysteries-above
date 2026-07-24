package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolyAffinityTest {

    @Test
    void classifiesKnownDarkPathwaysAsDark() {
        assertTrue(HolyAffinity.isDark("Death"));
        assertTrue(HolyAffinity.isDark("Darkness"));
        assertTrue(HolyAffinity.isDark("Chained"));
        assertTrue(HolyAffinity.isDark("HangedMan"));
        assertTrue(HolyAffinity.isDark("Abyss"));
    }

    @Test
    void classifiesNeutralPathwaysAsNotDark() {
        assertFalse(HolyAffinity.isDark("Sun"));
        assertFalse(HolyAffinity.isDark("Fool"));
        assertFalse(HolyAffinity.isDark("Visionary"));
    }

    @Test
    void treatsUnknownOrNullPathwayAsNotDark() {
        assertFalse(HolyAffinity.isDark(null));
        assertFalse(HolyAffinity.isDark("NotARealPathway"));
    }

    @Test
    void fullDamageMultiplierAgainstDarkPathways() {
        assertEquals(1.0, HolyAffinity.damageMultiplier("Death"));
        assertEquals(1.0, HolyAffinity.damageMultiplier("Abyss"));
    }

    @Test
    void reducedDamageMultiplierAgainstNeutralPathways() {
        assertEquals(0.6, HolyAffinity.damageMultiplier("Sun"));
        assertEquals(0.6, HolyAffinity.damageMultiplier(null));
    }

    @Test
    void booleanOverloadMatchesStringClassification() {
        assertEquals(1.0, HolyAffinity.damageMultiplier(true));
        assertEquals(0.6, HolyAffinity.damageMultiplier(false));
    }
}
