package me.vangoo.domain.rituals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RitualCatalogTest {

    @Test
    void catalogHasSevenRituals() {
        assertEquals(7, RitualCatalog.ALL.size());
    }

    @Test
    void sequenceGatingIsThreeFiveSeven() {
        assertEquals(3, RitualCatalog.availableFor(9).size());
        assertEquals(5, RitualCatalog.availableFor(8).size());
        assertEquals(7, RitualCatalog.availableFor(7).size());
        assertEquals(7, RitualCatalog.availableFor(0).size()); // нижче — все лишається
    }

    @Test
    void seqSevenRitualsNeedFiveCandlesOthersThree() {
        for (RitualRecipe r : RitualCatalog.ALL) {
            if (r.minSequence() == 7) {
                assertEquals(5, r.candlesRequired(), r.displayName());
            } else {
                assertEquals(3, r.candlesRequired(), r.displayName());
            }
        }
    }

    @Test
    void onlySacrificeUsesHandItemAndHasNoIngredientList() {
        for (RitualRecipe r : RitualCatalog.ALL) {
            if (r.type() == RitualType.SACRIFICE) {
                assertTrue(r.requiresHandSacrifice());
                assertTrue(r.ingredients().isEmpty());
            } else {
                assertFalse(r.requiresHandSacrifice(), r.displayName());
                assertFalse(r.ingredients().isEmpty(), r.displayName());
            }
        }
    }

    @Test
    void everyRitualResolvableByType() {
        for (RitualType type : RitualType.values()) {
            assertEquals(type, RitualCatalog.of(type).type());
        }
    }

    @Test
    void namesAndDescriptionsAreUkrainianNonEmpty() {
        for (RitualRecipe r : RitualCatalog.ALL) {
            assertFalse(r.displayName().isBlank());
            assertFalse(r.description().isBlank());
        }
    }
}
