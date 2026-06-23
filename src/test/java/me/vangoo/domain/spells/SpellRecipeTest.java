package me.vangoo.domain.spells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Баланс заклинань тепер тестується без Bukkit/Mockito — раніше {@code calculateStats}
 * був замкнений у GUI-класі {@code Spellcasting} і покрити його було неможливо.
 */
class SpellRecipeTest {

    @Test
    void aoeScalesWithPowerAndArea() {
        SpellRecipe r = SpellRecipe.fromBlueprint(
                new SpellBlueprint("Гнів", SpellRecipe.Shape.AOE, 2, 3, 0, 0, 0, null));

        assertEquals(8.0, r.damage());          // 4 + 2*2.0
        assertEquals(7.5, r.radius());          // 3 + 3*1.5
        assertEquals(65, r.spiritualityCost()); // 40 + 2*5 + 3*5
        assertEquals(15, r.cooldownSeconds());  // max(1, 10-0) + 5
        assertNull(r.buff());
    }

    @Test
    void costReductionFloorsAtFive() {
        SpellRecipe r = SpellRecipe.fromBlueprint(
                new SpellBlueprint("Дешеве", SpellRecipe.Shape.PROJECTILE, 0, 0, 0, 0, 5, null));

        assertEquals(5, r.spiritualityCost()); // max(5, 25 - 5*5)
    }

    @Test
    void buffShapeRequiresBuffType() {
        assertThrows(IllegalArgumentException.class, () ->
                new SpellRecipe("X", SpellRecipe.Shape.BUFF, 0, 0, 0, 100, null, 0, 10, 10));
    }

    @Test
    void codecRoundTripsDamageSpell() {
        SpellRecipe original = SpellRecipe.fromBlueprint(
                new SpellBlueprint("вогняний спис", SpellRecipe.Shape.AOE, 4, 2, 0, 1, 0, null));

        SpellRecipe restored = SpellCodec.decode(SpellCodec.encode(original));

        assertEquals(original, restored);
    }

    @Test
    void codecRoundTripsBuffSpell() {
        SpellRecipe original = SpellRecipe.fromBlueprint(
                new SpellBlueprint("аура сили", SpellRecipe.Shape.BUFF, 0, 3, 1, 0, 0, SpellRecipe.Buff.STRENGTH));

        SpellRecipe restored = SpellCodec.decode(SpellCodec.encode(original));

        assertEquals(SpellRecipe.Buff.STRENGTH, restored.buff());
        assertEquals(original, restored);
    }
}
