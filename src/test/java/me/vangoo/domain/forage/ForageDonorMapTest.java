package me.vangoo.domain.forage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForageDonorMapTest {

    private ForageDonorMap map() {
        return new ForageDonorMap("WARPED_ROOTS", "AZALEA_LEAVES",
                Map.of("POPPY", "CRIMSON_ROOTS"));
    }

    @Test
    void plantDefaultWhenNoOverride() {
        assertEquals("WARPED_ROOTS", map().donorFor("SHORT_GRASS", false));
    }

    @Test
    void leavesDefaultWhenNoOverride() {
        assertEquals("AZALEA_LEAVES", map().donorFor("OAK_LEAVES", true));
    }

    @Test
    void overrideBeatsDefault() {
        assertEquals("CRIMSON_ROOTS", map().donorFor("POPPY", false));
    }

    @Test
    void namesAreCaseInsensitive() {
        assertEquals("CRIMSON_ROOTS", map().donorFor("poppy", false));
    }

    @Test
    void unknownOriginalFallsBackToClassDefault() {
        assertEquals("WARPED_ROOTS", map().donorFor("DANDELION", false));
        assertEquals("AZALEA_LEAVES", map().donorFor("BIRCH_LEAVES", true));
    }
}
