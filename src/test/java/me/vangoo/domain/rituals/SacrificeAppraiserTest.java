package me.vangoo.domain.rituals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SacrificeAppraiserTest {

    @Test
    void pathwayIngredientIsTheBestSacrifice() {
        assertEquals(300, SacrificeAppraiser.spiritualityFor(SacrificeKind.PATHWAY_INGREDIENT));
    }

    @Test
    void valueStrictlyDecreasesByKind() {
        int ingredient = SacrificeAppraiser.spiritualityFor(SacrificeKind.PATHWAY_INGREDIENT);
        int precious = SacrificeAppraiser.spiritualityFor(SacrificeKind.PRECIOUS);
        int valuable = SacrificeAppraiser.spiritualityFor(SacrificeKind.VALUABLE);
        int trifle = SacrificeAppraiser.spiritualityFor(SacrificeKind.TRIFLE);
        assertTrue(ingredient > precious);
        assertTrue(precious > valuable);
        assertTrue(valuable > trifle);
        assertTrue(trifle > 0);
    }
}
