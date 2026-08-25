package me.vangoo.domain.forage;

import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.creatures.ConvergenceBias;
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

    /** Рецепти: Error 6 потребує "next", Error 7 — "current", Visionary 6 — "other". */
    private static final Map<String, Map<Integer, RecipeDefinition>> RECIPES = Map.of(
            "Error", Map.of(
                    6, new RecipeDefinition(List.of(), List.of("next")),
                    7, new RecipeDefinition(List.of(), List.of("current"))),
            "Visionary", Map.of(
                    6, new RecipeDefinition(List.of(), List.of("other"))));

    private ForageSelector biased() {
        return new ForageSelector(Map.of("PLAINS", List.of(
                new ForageEntry("next", 10),
                new ForageEntry("current", 10),
                new ForageEntry("other", 10))), RECIPES);
    }

    @Test
    void convergenceFavoursNextSequenceOfOwnPathway() {
        // Посл. 7 гравця Error: "next" (Посл. 6) x4 = 40, "current" (Посл. 7) x2 = 20, "other" x1 = 10
        ForageSelector s = biased();
        ConvergenceBias bias = new ConvergenceBias("Error", 7);
        assertEquals("next", s.pickForBiome("PLAINS", bias, 0.5).get());     // target 35 -> next [0,40)
        assertEquals("current", s.pickForBiome("PLAINS", bias, 0.7).get());  // target 49 -> current [40,60)
        assertEquals("other", s.pickForBiome("PLAINS", bias, 0.95).get());   // target 66.5 -> other [60,70)
    }

    @Test
    void convergenceNeverDropsForeignIngredients() {
        ForageSelector s = biased();
        ConvergenceBias bias = new ConvergenceBias("Error", 7);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) s.pickForBiome("PLAINS", bias, i / 1000.0).ifPresent(seen::add);
        assertEquals(java.util.Set.of("next", "current", "other"), seen);
    }

    @Test
    void nullBiasKeepsPlainBiomeWeights() {
        ForageSelector s = biased();
        assertEquals(s.pickForBiome("PLAINS", 0.5), s.pickForBiome("PLAINS", null, 0.5));
        assertEquals("current", s.pickForBiome("PLAINS", 0.5).get()); // рівні ваги: 10/10/10
    }

    @Test
    void pathwayMatchIsCaseInsensitive() {
        ForageSelector s = biased();
        assertEquals(s.pickForBiome("PLAINS", new ConvergenceBias("Error", 7), 0.5),
                     s.pickForBiome("PLAINS", new ConvergenceBias("error", 7), 0.5));
    }
}
