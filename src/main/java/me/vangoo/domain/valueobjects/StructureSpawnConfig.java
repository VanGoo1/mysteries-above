package me.vangoo.domain.valueobjects;

import org.bukkit.block.Biome;

import java.util.List;

public record StructureSpawnConfig(
        List<Biome> allowedBiomes,
        double spawnChance,
        int minDistance
) {
    public StructureSpawnConfig {
        if (spawnChance < 0 || spawnChance > 1) {
            throw new IllegalArgumentException("Spawn chance must be 0.0-1.0");
        }
        if (minDistance < 0) {
            throw new IllegalArgumentException("Min distance must be >= 0");
        }

        if (allowedBiomes == null) {
            allowedBiomes = List.of();
        } else {
            allowedBiomes = List.copyOf(allowedBiomes);
        }
    }

    /**
     * Check if biome is allowed for spawning
     */
    public boolean isBiomeAllowed(Biome biome) {
        return allowedBiomes.isEmpty() || allowedBiomes.contains(biome);
    }

    /**
     * Roll for spawn based on chance
     */
    public boolean shouldSpawn() {
        return Math.random() < spawnChance;
    }
}