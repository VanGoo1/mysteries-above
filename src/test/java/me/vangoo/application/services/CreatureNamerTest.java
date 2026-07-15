package me.vangoo.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreatureNamerTest {

    @Test
    void translatesBiomesUsedByCreaturesYml() {
        assertEquals("Пустеля", CreatureNamer.biomeName("DESERT"));
        assertEquals("Мангрові болота", CreatureNamer.biomeName("MANGROVE_SWAMP"));
        assertEquals("Теплий океан", CreatureNamer.biomeName("WARM_OCEAN"));
        assertEquals("Темний ліс", CreatureNamer.biomeName("DARK_FOREST"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("Джунглі", CreatureNamer.biomeName("jungle"));
    }

    /** Новий біом у creatures.yml не мусить ламати меню — лише читається негарно. */
    @Test
    void unknownBiomeFallsBackToHumanizedName() {
        assertEquals("Cherry grove", CreatureNamer.biomeName("CHERRY_GROVE"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", CreatureNamer.biomeName(null));
        assertEquals("", CreatureNamer.biomeName("  "));
    }
}
