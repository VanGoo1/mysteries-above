package me.vangoo.domain.valueobjects;

import java.util.List;
import java.util.Objects;

/**
 * Domain Value Object: Represents a structure definition
 */
public record Structure(
        String id,
        String nbtFileName,
        StructureSpawnConfig spawnConfig,
        List<LootTable> lootTables
) {
    public Structure {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(nbtFileName, "NBT file cannot be null");
        Objects.requireNonNull(spawnConfig, "Spawn config cannot be null");

        if (lootTables == null) {
            lootTables = List.of();
        } else {
            lootTables = List.copyOf(lootTables);
        }
    }

    /**
     * Check if structure has loot tables
     */
    public boolean hasLootTables() {
        return !lootTables.isEmpty();
    }
}