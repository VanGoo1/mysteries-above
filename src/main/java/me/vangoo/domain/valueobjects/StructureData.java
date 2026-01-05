package me.vangoo.domain.valueobjects;

import org.bukkit.block.Biome;
import org.bukkit.structure.Structure;

import java.util.List;

public record StructureData(
        String id,
        Structure structure,
        List<Biome> biomes,
        double spawnChance,
        int minDistance,
        StructurePlacementType placementType,
        LootTableData lootTable
) {
    // Конструктор з дефолтними значеннями для зворотної сумісності
    public StructureData(
            String id,
            Structure structure,
            List<Biome> biomes,
            double spawnChance,
            LootTableData lootTable
    ) {
        this(id, structure, biomes, spawnChance, 500, StructurePlacementType.SURFACE, lootTable);
    }
}