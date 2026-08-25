package me.vangoo.domain.rituals;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureTier;
import me.vangoo.domain.creatures.SpawnRule;
import me.vangoo.domain.forage.ForageEntry;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IngredientSourceIndexTest {

    private static CreatureDefinition creature(String id, String ingredient, String... biomes) {
        return new CreatureDefinition(id, "ZOMBIE", CreatureTier.COMMON,
                new LootTableData(List.of(new LootItem(ingredient, 50, 1, 1)), 0, 1),
                new SpawnRule(List.of(biomes), List.of(), 0.005, 0.0),
                "Error", 9);
    }

    @Test
    void creatureLootMapsToItsSpawnBiomes() {
        var index = new IngredientSourceIndex(
                List.of(creature("c1", "rat_heart", "SWAMP", "DARK_FOREST")), Map.of());

        var source = index.sourceOf("rat_heart").orElseThrow();
        assertEquals(IngredientSourceIndex.Kind.CREATURE, source.kind());
        assertEquals(List.of("SWAMP", "DARK_FOREST"), source.biomes());
    }

    @Test
    void biomesOfSeveralCreaturesDroppingTheSameIngredientMerge() {
        var index = new IngredientSourceIndex(List.of(
                creature("c1", "rat_heart", "SWAMP"),
                creature("c2", "rat_heart", "SWAMP", "JUNGLE")), Map.of());

        assertEquals(List.of("SWAMP", "JUNGLE"), index.sourceOf("rat_heart").orElseThrow().biomes());
    }

    @Test
    void forageIngredientIsIndexedByTheBiomesThatOfferIt() {
        var index = new IngredientSourceIndex(List.of(), Map.of(
                "PLAINS", List.of(new ForageEntry("elf_flower_petals", 50))));

        var source = index.sourceOf("elf_flower_petals").orElseThrow();
        assertEquals(IngredientSourceIndex.Kind.FORAGE, source.kind());
        assertEquals(List.of("PLAINS"), source.biomes());
    }

    @Test
    void creatureWinsOverForageForTheSameIngredient() {
        var index = new IngredientSourceIndex(
                List.of(creature("c1", "shared", "SWAMP")),
                Map.of("PLAINS", List.of(new ForageEntry("shared", 50))));

        assertEquals(IngredientSourceIndex.Kind.CREATURE, index.sourceOf("shared").orElseThrow().kind());
    }

    @Test
    void chestOnlyIngredientHasNoSource() {
        var index = new IngredientSourceIndex(List.of(), Map.of());
        assertTrue(index.sourceOf("master_recipe_book").isEmpty());
    }
}
