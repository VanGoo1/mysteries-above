package me.vangoo.domain.forage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForageSelectorTest {

    @Test
    void picksFromKnownBiome() {
        ForageSelector s = new ForageSelector(
                Map.of("PLAINS", List.of(new ForageEntry("elf_flower_petals", 50))));
        assertEquals("elf_flower_petals", s.pickForBiome("PLAINS", 0.5).orElse(null));
    }

    @Test
    void unknownBiomeEmpty() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS", List.of(new ForageEntry("x", 50))));
        assertTrue(s.pickForBiome("DESERT", 0.5).isEmpty());
    }

    @Test
    void nullBiomeEmpty() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS", List.of(new ForageEntry("x", 50))));
        assertTrue(s.pickForBiome(null, 0.5).isEmpty());
    }

    @Test
    void emptyEntriesEmpty() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS", List.<ForageEntry>of()));
        assertTrue(s.pickForBiome("PLAINS", 0.5).isEmpty());
    }

    @Test
    void weightedSegmentsByRoll() {
        // sum 80; a:[0,50) b:[50,80)
        ForageSelector s = new ForageSelector(Map.of("PLAINS",
                List.of(new ForageEntry("a", 50), new ForageEntry("b", 30))));
        assertEquals("a", s.pickForBiome("PLAINS", 0.1).get());   // target 8  -> a
        assertEquals("a", s.pickForBiome("PLAINS", 0.6).get());   // target 48 -> a
        assertEquals("b", s.pickForBiome("PLAINS", 0.7).get());   // target 56 -> b
        assertEquals("b", s.pickForBiome("PLAINS", 0.99).get());  // target ~79 -> b
    }

    @Test
    void zeroWeightIgnored() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS",
                List.of(new ForageEntry("zero", 0), new ForageEntry("real", 10))));
        assertEquals("real", s.pickForBiome("PLAINS", 0.0).get());
    }
}
