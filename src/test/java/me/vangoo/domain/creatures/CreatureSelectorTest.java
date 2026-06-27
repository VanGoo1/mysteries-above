package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreatureSelectorTest {

    private CreatureDefinition def(String id, SpawnRule spawn) {
        return new CreatureDefinition(
                id, "GUARDIAN", "§3" + id, CreatureTier.COMMON,
                new CreatureStats(30, 6, 0.25, 1.2),
                Map.of(), "vanilla",
                new LootTableData(List.of(), 1, 2),
                spawn, true, "visionary");
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
}
