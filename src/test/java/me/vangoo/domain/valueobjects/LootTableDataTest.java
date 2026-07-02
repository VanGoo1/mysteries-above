package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootTableDataTest {

    @Test
    void filterKeepsOnlyAllowedTier() {
        LootItem base = new LootItem("a", 1, 1, 1, LootTier.BASE);
        LootItem rare = new LootItem("b", 1, 1, 1, LootTier.RARE);
        LootTableData t = new LootTableData(List.of(base, rare), 1, 2);
        LootTableData onlyBase = t.filterByTier(EnumSet.of(LootTier.BASE));
        assertEquals(1, onlyBase.items().size());
        assertEquals("a", onlyBase.items().get(0).itemId());
    }

    @Test
    void filterBothTiersKeepsAll() {
        LootTableData t = new LootTableData(List.of(
                new LootItem("a", 1, 1, 1, LootTier.BASE),
                new LootItem("b", 1, 1, 1, LootTier.RARE)), 1, 2);
        assertEquals(2, t.filterByTier(EnumSet.allOf(LootTier.class)).items().size());
    }

    @Test
    void defaultConstructorIsBase() {
        assertEquals(LootTier.BASE, new LootItem("a", 1, 1, 1).tier());
    }

    @Test
    void filterPreservesMinMax() {
        LootTableData t = new LootTableData(List.of(new LootItem("a", 1, 1, 1, LootTier.BASE)), 3, 5);
        LootTableData f = t.filterByTier(EnumSet.of(LootTier.BASE));
        assertEquals(3, f.minItems());
        assertEquals(5, f.maxItems());
    }

    @Test
    void emptyAllowedYieldsEmpty() {
        LootTableData t = new LootTableData(List.of(new LootItem("a", 1, 1, 1, LootTier.BASE)), 1, 2);
        assertTrue(t.filterByTier(EnumSet.noneOf(LootTier.class)).items().isEmpty());
    }
}
