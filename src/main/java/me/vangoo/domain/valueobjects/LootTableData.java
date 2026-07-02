package me.vangoo.domain.valueobjects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LootTableData(List<LootItem> items, int minItems, int maxItems) {
    public LootTableData(List<LootItem> items) {
        this(items, 3, 8);
    }

    public LootTableData {
        Objects.requireNonNull(items, "items must not be null");
        if (minItems < 0) minItems = 0;
        if (maxItems < minItems) maxItems = minItems;
    }

    /** Підмножина таблиці лише з предметами дозволених тірів (зберігає minItems/maxItems). */
    public LootTableData filterByTier(Set<LootTier> allowed) {
        List<LootItem> filtered = new ArrayList<>();
        for (LootItem i : items) {
            if (allowed.contains(i.tier())) filtered.add(i);
        }
        return new LootTableData(filtered, minItems, maxItems);
    }
}
