package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuybackPriceTableTest {

    private final BuybackPriceTable table = new BuybackPriceTable(
            2,   // інгредієнт за замовчуванням
            10,  // книга рецептів
            Map.of(9, 40, 8, 60),                     // Характеристики за seq (коппети)
            Map.of("custom:stellar_aqua_crystal", 5)  // точковий override
    );

    @Test
    void usesCategoryDefaults() {
        assertEquals(2, table.unitPriceFor(MarketItemCategory.INGREDIENT, -1, "custom:crimson_star").coppets());
        assertEquals(10, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 9, "recipe:Fool:9").coppets());
    }

    @Test
    void characteristicPricedBySequence() {
        assertEquals(40, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 9, "characteristic:Fool:9").coppets());
        assertEquals(60, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 8, "characteristic:Door:8").coppets());
    }

    @Test
    void unknownCharacteristicSequenceFallsBackToZero() {
        // seq без запису в таблиці → 0 к (організатор таке не скуповує)
        assertEquals(0, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 0, "characteristic:Fool:0").coppets());
    }

    @Test
    void exactOverrideWinsOverCategoryDefault() {
        assertEquals(5, table.unitPriceFor(MarketItemCategory.INGREDIENT, -1, "custom:stellar_aqua_crystal").coppets());
    }
}
