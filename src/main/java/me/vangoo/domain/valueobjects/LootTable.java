package me.vangoo.domain.valueobjects;

import java.util.List;
import java.util.Objects;

/**
 * Domain Value Object: Loot table configuration for chests
 */
public record LootTable(
        String chestTag,
        List<LootEntry> items
) {
    public LootTable {
        Objects.requireNonNull(chestTag, "Chest tag cannot be null");

        if (items == null) {
            items = List.of();
        } else {
            items = List.copyOf(items);
        }
    }

    /**
     * Represents a single loot entry
     */
    public record LootEntry(
            String itemId,
            int weight,
            int amountMin,
            int amountMax
    ) {
        public LootEntry {
            if (weight < 1) {
                throw new IllegalArgumentException("Weight must be >= 1");
            }
            if (amountMin < 1 || amountMax < amountMin) {
                throw new IllegalArgumentException("Invalid amount range");
            }
        }

        /**
         * Get random amount in range
         */
        public int getRandomAmount() {
            if (amountMin == amountMax) {
                return amountMin;
            }
            return amountMin + (int) (Math.random() * (amountMax - amountMin + 1));
        }
    }
}