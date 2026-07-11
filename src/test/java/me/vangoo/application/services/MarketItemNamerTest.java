package me.vangoo.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketItemNamerTest {

    // Non-custom branches never touch CustomItemService, so null is safe here.
    private final MarketItemNamer namer = new MarketItemNamer(null);

    @Test
    void formatsRecipeKey() {
        assertEquals("Книга рецептів (Fool, Посл. 9)", namer.displayName("recipe:Fool:9"));
    }

    @Test
    void formatsCharacteristicKey() {
        assertEquals("Характеристика (Door, Посл. 8)", namer.displayName("characteristic:Door:8"));
    }

    @Test
    void humanizesUnknownKey() {
        assertEquals("Dimensional wanderer eye", namer.displayName("custom:dimensional_wanderer_eye"));
    }
}
