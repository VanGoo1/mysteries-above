package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreatureSelectorTest {

    private CreatureDefinition def(String id, SpawnRule spawn) {
        return def(id, spawn, "visionary", 9);
    }

    private CreatureDefinition def(String id, SpawnRule spawn, String pathway, int sequence) {
        return new CreatureDefinition(
                id, "GUARDIAN", "§3" + id, CreatureTier.COMMON,
                new CreatureStats(30, 6, 0.25, 1.2),
                Map.of(), "vanilla",
                new LootTableData(List.of(), 1, 2),
                spawn, true, pathway, sequence);
    }

    private SpawnRule natural(double chance) {
        return new SpawnRule(List.of("OCEAN"), List.of("GUARDIAN"), chance, List.of(), 0.0);
    }

    private SpawnRule structure(double chance) {
        return new SpawnRule(List.of(), List.of(), 0.0, List.of("mysteries"), chance);
    }

    @Test
    void biomeMatchWithinChanceReturnsCreature() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.4).isPresent());
    }

    @Test
    void biomeRollBeyondChanceReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.6).isEmpty());
    }

    @Test
    void wrongBiomeReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("PLAINS", "GUARDIAN", 0.1).isEmpty());
    }

    @Test
    void wrongBaseEntityReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("OCEAN", "ZOMBIE", 0.1).isEmpty());
    }

    @Test
    void structureKeyContainsMatchReturnsCreature() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", structure(0.5))));
        assertTrue(s.pickForStructure("minecraft:mysteries/dungeon", 0.4).isPresent());
    }

    @Test
    void structureKeyNoMatchReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", structure(0.5))));
        assertTrue(s.pickForStructure("minecraft:village/house", 0.1).isEmpty());
    }

    @Test
    void weightedSegmentsPickByRoll() {
        CreatureDefinition a = def("a", natural(0.2));
        CreatureDefinition b = def("b", natural(0.3));
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.1).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.35).get().id());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.9).isEmpty());
    }

    @Test
    void emptyRegistryReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.0).isEmpty());
        assertTrue(s.pickForStructure("mysteries", 0.0).isEmpty());
    }

    @Test
    void noBiasOverloadMatchesThreeArg() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertEquals(s.pickForBiome("OCEAN", "GUARDIAN", 0.4),
                     s.pickForBiome("OCEAN", "GUARDIAN", 0.4, null));
    }

    @Test
    void convergencePreservesTotalSpawnProbability() {
        // two visionary candidates, seq 9 and seq 8, base chance 0.2 each -> total 0.4
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8);
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9); // next-needed = 8 (x4), current = 9 (x2)

        // total spawn probability unchanged: roll just under 0.4 spawns, 0.4 does not
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.399, bias).isPresent());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.4, bias).isEmpty());
    }

    @Test
    void convergenceFavorsNextNeededSequence() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9); // current -> x2
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8); // next-needed -> x4
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        // weights: a=0.4, b=0.8, sum=1.2, scale=0.4/1.2 -> a=0.1333, b=0.2667
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.10, bias).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.20, bias).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.39, bias).get().id());
    }

    @Test
    void convergenceForUnrelatedPathwayIsNoOp() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8);
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias unrelated = new ConvergenceBias("Error", 5);
        // no candidate matches -> same segments as no-bias: a in [0,0.2), b in [0.2,0.4)
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.10, unrelated).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.30, unrelated).get().id());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.40, unrelated).isEmpty());
    }

    @Test
    void biasAgainstNullPathwayCreatureIsNoOp() {
        CreatureDefinition noPathway = new CreatureDefinition("x", "GUARDIAN", "§3x",
                CreatureTier.COMMON, new CreatureStats(30, 6, 0.25, 1.2),
                Map.of(), "vanilla", new LootTableData(List.of(), 1, 2),
                natural(0.3), true, null, 0);
        CreatureSelector s = new CreatureSelector(List.of(noPathway));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.1, bias).isPresent());
    }
}
