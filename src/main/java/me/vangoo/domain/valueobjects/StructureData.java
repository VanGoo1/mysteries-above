package me.vangoo.domain.valueobjects;

import org.bukkit.block.Biome;
import org.bukkit.structure.Structure;

import java.util.List;
import java.util.Map;

/**
 * Domain Value Object: Represents a structure definition
 */
public record StructureData(
        String id,
        Structure structure,
        List<Biome> biomes,
        double spawnChance,
        Map<String, LootTableData> lootTables
) {
}

