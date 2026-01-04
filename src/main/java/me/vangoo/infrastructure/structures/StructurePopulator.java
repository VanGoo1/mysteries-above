package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.StructureData;
import me.vangoo.domain.valueobjects.StructurePlacementType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructurePopulator extends BlockPopulator {

    private final Plugin plugin;
    private final Map<String, StructureData> structures;
    private final LootGenerationService lootGenerationService;

    // Зберігаємо позиції спавнів для кожної структури окремо
    private final Map<String, Set<ChunkCoord>> spawnedStructures = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public StructurePopulator(Plugin plugin,
                              NBTStructureConfigLoader configLoader,
                              LootGenerationService lootGenerationService) {
        this.plugin = plugin;
        this.lootGenerationService = lootGenerationService;
        this.structures = configLoader.loadAllStructures();

        // Ініціалізуємо Set для кожної структури
        structures.keySet().forEach(id -> spawnedStructures.put(id, ConcurrentHashMap.newKeySet()));

        plugin.getLogger().info("StructurePopulator initialized with " + structures.size() + " structures.");
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        // Використовуємо регіональний підхід: перевіряємо тільки якщо chunk координати кратні певному числу
        // Це зменшує частоту спроб спавну
        int regionSize = 8; // Перевіряємо тільки кожен 8-й чанк по обох осях
        if (chunk.getX() % regionSize != 0 || chunk.getZ() % regionSize != 0) {
            return;
        }

        for (StructureData data : structures.values()) {
            // Перевірка шансу (тепер це реально рідкісніше, бо перевіряємо рідше)
            if (random.nextDouble() > data.spawnChance()) continue;

            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);

            ChunkCoord currentCoord = new ChunkCoord(chunk.getX(), chunk.getZ());

            // Перевірка мінімальної відстані до інших спавнів цієї ж структури
            if (!canSpawnAt(data, currentCoord)) {
                continue;
            }

            Location spawnLoc = findValidSpawnLocation(world, x, z, data);
            if (spawnLoc == null) continue;

            // Перевірка біому
            if (!data.biomes().isEmpty() && !data.biomes().contains(world.getBiome(spawnLoc))) {
                continue;
            }

            if (spawnStructure(data, spawnLoc)) {
                // Зберігаємо координати успішного спавну
                spawnedStructures.get(data.id()).add(currentCoord);
                plugin.getLogger().info("Spawned structure " + data.id() +
                        " at " + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
                break; // Одна структура на регіон
            }
        }
    }

    private boolean canSpawnAt(StructureData data, ChunkCoord coord) {
        Set<ChunkCoord> existing = spawnedStructures.get(data.id());
        if (existing.isEmpty()) return true;

        int minDistanceChunks = data.minDistance() / 16; // Конвертуємо блоки в чанки

        for (ChunkCoord existingCoord : existing) {
            int dx = Math.abs(coord.x - existingCoord.x);
            int dz = Math.abs(coord.z - existingCoord.z);
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < minDistanceChunks) {
                return false;
            }
        }

        return true;
    }

    private Location findValidSpawnLocation(World world, int x, int z, StructureData data) {
        switch (data.placementType()) {
            case SURFACE:
                return findSurfaceLocation(world, x, z);
            case LIQUID_SURFACE:
                return findLiquidSurfaceLocation(world, x, z);
            case SKY:
                return findSkyLocation(world, x, z);
            default:
                return findSurfaceLocation(world, x, z);
        }
    }

    private Location findSurfaceLocation(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Block block = world.getBlockAt(x, y - 1, z);

        // Перевіряємо чи це твердий блок
        if (block.getType().isSolid() && !block.isLiquid()) {
            return new Location(world, x, y, z);
        }
        return null;
    }

    private Location findLiquidSurfaceLocation(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Block block = world.getBlockAt(x, y - 1, z);

        // Перевіряємо чи це вода або лава
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            return new Location(world, x, y, z);
        }
        return null;
    }

    private Location findSkyLocation(World world, int x, int z) {
        // Спавнимо високо в небі (між 150-200 блоками)
        int y = 150 + random.nextInt(50);
        return new Location(world, x, y, z);
    }

    private boolean spawnStructure(StructureData data, Location location) {
        try {
            // 1. Розміщуємо структуру
            data.structure().place(
                    location,
                    true,
                    StructureRotation.values()[random.nextInt(4)],
                    org.bukkit.block.structure.Mirror.NONE,
                    -1,
                    1.0f,
                    random
            );

            // 2. Генерація луту з більшою затримкою і логуванням
            if (!data.lootTables().isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getLogger().info("Processing loot for structure " + data.id() +
                            " with " + data.lootTables().size() + " loot tables");
                    lootGenerationService.processStructureLoot(data, location);
                }, 60L); // 3 секунди затримки
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place structure " + data.id() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Set<String> getStructureIds() {
        return structures.keySet();
    }

    public boolean placeStructureManually(String id, Location location) {
        StructureData data = structures.get(id);
        if (data == null) return false;

        return spawnStructure(data, location);
    }

    public void clearSpawnHistory(String structureId) {
        if (spawnedStructures.containsKey(structureId)) {
            spawnedStructures.get(structureId).clear();
            plugin.getLogger().info("Cleared spawn history for " + structureId);
        }
    }

    public Map<String, Integer> getSpawnCounts() {
        Map<String, Integer> counts = new HashMap<>();
        spawnedStructures.forEach((id, coords) -> counts.put(id, coords.size()));
        return counts;
    }

    // Внутрішній клас для зберігання координат чанків
    private static class ChunkCoord {
        final int x;
        final int z;

        ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoord that)) return false;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}