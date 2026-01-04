package me.vangoo.domain.valueobjects;

import org.bukkit.block.Biome;
import org.bukkit.structure.Structure;

import java.util.List;
import java.util.Map;

public record StructureData(
        String id,
        Structure structure,
        List<Biome> biomes,
        double spawnChance,
        int minDistance,
        StructurePlacementType placementType,
        Map<String, LootTableData> lootTables
) {
    // Конструктор з дефолтними значеннями для зворотної сумісності
    public StructureData(
            String id,
            Structure structure,
            List<Biome> biomes,
            double spawnChance,
            Map<String, LootTableData> lootTables
    ) {
        this(id, structure, biomes, spawnChance, 500, StructurePlacementType.SURFACE, lootTables);
    }
}
