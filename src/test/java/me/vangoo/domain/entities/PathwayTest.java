package me.vangoo.domain.entities;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
