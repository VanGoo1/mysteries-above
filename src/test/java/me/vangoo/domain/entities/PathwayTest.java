package me.vangoo.domain.entities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє інваріант кількості послідовностей у базовому {@link Pathway}. Використовує
 * порожній стаб-підклас (без здібностей, без Bukkit), бо реальні шляхи тягнуть Bukkit-реєстри
 * і не тестуються headless.
 */
class PathwayTest {

    private static final List<String> TEN =
            List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

    /** Мінімальний шлях без здібностей — лише для перевірки інваріантів базового класу. */
    private static final class StubPathway extends Pathway {
        StubPathway(List<String> sequenceNames) {
            super(PathwayGroup.LordOfMysteries, sequenceNames);
        }

        @Override
        protected void initializeAbilities() {
            // тестовий стаб — здібності не потрібні
        }
    }

    /** Мінімальна конкретна здібність — лише для наповнення послідовності в тесті. */
    private static final class TestAbility extends ActiveAbility {
        @Override public String getName() { return "t"; }
        @Override public String getDescription(Sequence userSequence) { return "t"; }
        @Override public int getSpiritualityCost() { return 0; }
        @Override public int getCooldown(Sequence userSequence) { return 0; }
        @Override protected AbilityResult performExecution(IAbilityContext context) { return null; }
    }

    /** Шлях з однією здібністю на Seq 9 — для перевірки hasAnyAbility() == true. */
    private static final class OneAbilityPathway extends Pathway {
        OneAbilityPathway(List<String> sequenceNames) {
            super(PathwayGroup.LordOfMysteries, sequenceNames);
        }
        @Override
        protected void initializeAbilities() {
            sequenceAbilities.put(9, List.of(new TestAbility()));
        }
    }

    @Test
    void tenSequenceNamesIsAcceptedAndIndexableAtLevelNine() {
        Pathway p = new StubPathway(TEN);
        assertEquals("9", p.getSequenceName(9));
        assertEquals("0", p.getSequenceName(0));
    }

    @Test
    void fewerThanTenSequenceNamesThrows() {
        List<String> nine = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8");
        assertThrows(IllegalArgumentException.class, () -> new StubPathway(nine));
    }

    @Test
    void moreThanTenSequenceNamesThrows() {
        List<String> eleven = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThrows(IllegalArgumentException.class, () -> new StubPathway(eleven));
    }

    @Test
    void hasAnyAbilityIsFalseWhenNoAbilitiesRegistered() {
        assertFalse(new StubPathway(TEN).hasAnyAbility());
    }

    @Test
    void hasAnyAbilityIsTrueWhenAnAbilityIsRegistered() {
        assertTrue(new OneAbilityPathway(TEN).hasAnyAbility());
    }
}
