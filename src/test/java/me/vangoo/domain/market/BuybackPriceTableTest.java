package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuybackPriceTableTest {

    private static final Map<Integer, Integer> INGREDIENTS = Map.of(
            9, 3, 8, 5, 7, 8, 6, 13, 5, 20, 4, 32, 3, 50, 2, 75, 1, 105, 0, 140);
    private static final Map<Integer, Integer> RECIPE_BOOKS = Map.of(
            9, 120, 8, 170, 7, 240, 6, 330, 5, 450, 4, 600, 3, 780, 2, 980, 1, 1200, 0, 1500);
    private static final Map<Integer, Integer> CHARACTERISTICS = Map.of(
            9, 80, 8, 130, 7, 220, 6, 360, 5, 540, 4, 780, 3, 1100, 2, 1500, 1, 2000, 0, 2600);

    private final BuybackPriceTable table = new BuybackPriceTable(
            INGREDIENTS,
            2,   // фолбек для інгредієнта без послідовності
            RECIPE_BOOKS,
            CHARACTERISTICS,
            Map.of("custom:stellar_aqua_crystal", 5)); // точковий override

    @Test
    void ingredientPricedBySequence() {
        assertEquals(3, table.unitPriceFor(MarketItemCategory.INGREDIENT, 9, "custom:sphinx_brain").coppets());
        assertEquals(140, table.unitPriceFor(MarketItemCategory.INGREDIENT, 0, "custom:puppeteer_heartstring").coppets());
    }

    @Test
    void ingredientWithoutSequenceUsesFallback() {
        // seq = -1 (не входить у жоден рецепт) → плоский фолбек
        assertEquals(2, table.unitPriceFor(MarketItemCategory.INGREDIENT, -1, "custom:loot_only_thing").coppets());
    }

    @Test
    void recipeBookPricedBySequence() {
        assertEquals(120, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 9, "recipe:Fool:9").coppets());
        assertEquals(1500, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 0, "recipe:Door:0").coppets());
    }

    @Test
    void characteristicPricedBySequence() {
        assertEquals(80, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 9, "characteristic:Fool:9").coppets());
        assertEquals(2600, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 0, "characteristic:Door:0").coppets());
    }

    @Test
    void unknownSequenceFallsBackToZeroForPricedCategories() {
        // seq без запису в таблиці (окрім інгредієнта з його фолбеком) → 0 к
        assertEquals(0, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 42, "recipe:Fool:42").coppets());
        assertEquals(0, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 42, "characteristic:Fool:42").coppets());
    }

    @Test
    void exactOverrideWinsOverCategoryDefault() {
        assertEquals(5, table.unitPriceFor(MarketItemCategory.INGREDIENT, 9, "custom:stellar_aqua_crystal").coppets());
    }

    @Test
    void completeTablePricesEverySequenceForAllCategories() {
        for (int seq = 0; seq <= 9; seq++) {
            assertTrue(table.unitPriceFor(
                            MarketItemCategory.INGREDIENT, seq, "custom:x").coppets() > 0,
                    "ingredient seq " + seq + " must have a non-zero buyback price");
            assertTrue(table.unitPriceFor(
                            MarketItemCategory.RECIPE_BOOK, seq, "recipe:Fool:" + seq).coppets() > 0,
                    "recipe book seq " + seq + " must have a non-zero buyback price");
            assertTrue(table.unitPriceFor(
                            MarketItemCategory.CHARACTERISTIC, seq, "characteristic:Fool:" + seq).coppets() > 0,
                    "characteristic seq " + seq + " must have a non-zero buyback price");
        }
    }
}
