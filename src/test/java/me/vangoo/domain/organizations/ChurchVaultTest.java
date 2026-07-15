package me.vangoo.domain.organizations;

import me.vangoo.domain.brewing.BrewRecipe;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChurchVaultTest {

    private final BrewRecipe recipe = new BrewRecipe("Door", 9,
            Map.of("custom:night_vanilla", 2, "custom:lava_lily", 1),   // основні
            Map.of("custom:pure_water", 1));                             // допоміжні

    @Test
    void recipeKnowledgeIsGateNotFuel() {
        ChurchVault vault = new ChurchVault();
        assertFalse(vault.hasRecipeKnowledge("Door", 9));
        vault.add("recipe:Door:9", 1);
        assertTrue(vault.hasRecipeKnowledge("Door", 9));
        vault.add("custom:night_vanilla", 2);
        vault.add("custom:lava_lily", 1);
        vault.add("custom:pure_water", 1);
        assertTrue(vault.consumeFor(recipe));
        assertTrue(vault.hasRecipeKnowledge("Door", 9)); // книга лишається
    }

    @Test
    void consumesClassicVariantFirst() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:night_vanilla", 2);
        vault.add("custom:lava_lily", 1);
        vault.add("custom:pure_water", 1);
        vault.add("characteristic:Door:9", 1);
        assertTrue(vault.consumeFor(recipe));
        // класика списана, Характеристика вціліла
        assertEquals(0, vault.amountOf("custom:night_vanilla"));
        assertEquals(1, vault.amountOf("characteristic:Door:9"));
    }

    @Test
    void fallsBackToCharacteristicSubstitution() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:pure_water", 1);            // лише допоміжні
        vault.add("characteristic:Door:9", 1);        // + Характеристика-заміна
        assertTrue(vault.missingFor(recipe).isEmpty());
        assertTrue(vault.consumeFor(recipe));
        assertEquals(0, vault.amountOf("characteristic:Door:9"));
        assertEquals(0, vault.amountOf("custom:pure_water"));
    }

    @Test
    void missingForReportsShortfallAtomically() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:night_vanilla", 1); // треба 2
        Map<String, Integer> missing = vault.missingFor(recipe);
        assertEquals(1, missing.get("custom:night_vanilla"));
        assertEquals(1, missing.get("custom:lava_lily"));
        assertEquals(1, missing.get("custom:pure_water"));
        assertFalse(vault.consumeFor(recipe));
        assertEquals(1, vault.amountOf("custom:night_vanilla")); // нічого не списано
    }

    @Test
    void conservationInvariantHolds() {
        // «зайшло» = «лежить» + «списано у варіння»
        ChurchVault vault = new ChurchVault();
        vault.add("custom:pure_water", 5);
        vault.add("characteristic:Door:9", 2);
        int inflow = 5 + 2;
        assertTrue(vault.consumeFor(recipe)); // списує 1 воду + 1 Характеристику
        int stored = vault.snapshot().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(inflow - 2, stored);
    }

    @Test
    void orderReadiness() {
        PotionOrder order = new PotionOrder("Door", 9, 1_000L, 60);
        assertFalse(order.isReady(999L));
        assertTrue(order.isReady(1_000L));
    }
}
