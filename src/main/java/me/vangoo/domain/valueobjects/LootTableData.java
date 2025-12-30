package me.vangoo.domain.valueobjects;

import java.util.List;
import java.util.Objects;

public record LootTableData(List<LootItem> items, int minItems, int maxItems) {
    public LootTableData(List<LootItem> items) {
        this(items, 3, 8);
    }

    public LootTableData {
        Objects.requireNonNull(items, "items must not be null");
        if (minItems < 0) minItems = 0;
        if (maxItems < minItems) maxItems = minItems;
    }
}
