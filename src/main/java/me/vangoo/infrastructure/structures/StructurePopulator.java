package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.StructureData;
import me.vangoo.domain.valueobjects.StructurePlacementType;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public class StructurePopulator extends BlockPopulator {

    private final Plugin plugin;
    private final Map<String, StructureData> structures;
    private final LootGenerationService lootGenerationService;
    private final Map<String, Set<BlockPos>> spawnedStructures = new ConcurrentHashMap<>();
    private final Map<String, Set<Biome>> spawnedBiomes = new ConcurrentHashMap<>();

    // Конфігурація генерації (ОПТИМІЗОВАНО)
    private static final class GenerationConfig {
        static final int CHECK_RADIUS = 8;
        static final int MAX_HEIGHT_DIFFERENCE = 3;
        static final int MIN_DISTANCE_TO_ANY_STRUCTURE = 450;
        static final int FILL_UNDER_MAX_DEPTH = 6;
        static final int SAMPLE_STRIDE = 2;
        static final int REGION_SIZE = 18;
        static final int MAX_SCAN_DEPTH = 40;
        static final long LOOT_GENERATION_DELAY_TICKS = 60L;
    }

    public StructurePopulator(Plugin plugin,
                              NBTStructureConfigLoader configLoader,
                              LootGenerationService lootGenerationService) {
        this.plugin = plugin;
        this.lootGenerationService = lootGenerationService;
        this.structures = configLoader.loadAllStructures();

        structures.keySet().forEach(id -> {
            spawnedStructures.put(id, ConcurrentHashMap.newKeySet());
            spawnedBiomes.put(id, ConcurrentHashMap.newKeySet());
        });
        plugin.getLogger().info("StructurePopulator initialized with " + structures.size() + " structures.");
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        if (!shouldProcessChunk(chunk, random)) {
            return;
        }

        for (StructureData data : structures.values()) {
            if (trySpawnStructureInChunk(world, chunk, random, data)) {
                break; // лише одна структура на регіон
            }
        }
    }

    private boolean shouldProcessChunk(Chunk chunk, Random random) {
        int offsetX = random.nextInt(GenerationConfig.REGION_SIZE);
        int offsetZ = random.nextInt(GenerationConfig.REGION_SIZE);

        return (chunk.getX() + offsetX) % GenerationConfig.REGION_SIZE == 0 &&
                (chunk.getZ() + offsetZ) % GenerationConfig.REGION_SIZE == 0;
    }

    private boolean trySpawnStructureInChunk(World world, Chunk chunk, Random random, StructureData data) {
        int x = (chunk.getX() << 4) + random.nextInt(16);
        int z = (chunk.getZ() << 4) + random.nextInt(16);

        Location testLoc = new Location(world, x, 64, z);
        Biome currentBiome = world.getBiome(testLoc);

        Set<Biome> alreadySpawnedBiomes = spawnedBiomes.get(data.id());
        boolean isTargetBiome = data.biomes().contains(currentBiome);
        boolean isFirstSpawnInBiome = isTargetBiome && !alreadySpawnedBiomes.contains(currentBiome);

        double effectiveChance = data.spawnChance();

        if (isFirstSpawnInBiome) {
            if (isBiomeStable(world, x, z, currentBiome)) {
                effectiveChance = Math.min(0.65, data.spawnChance() * 10.0);
            }
        } else if (!isTargetBiome) {
            return false;
        }

        if (random.nextDouble() > effectiveChance) {
            return false;
        }

        Location spawnLoc = findValidSpawnLocation(world, data, x, z);
        if (spawnLoc == null) {
            return false;
        }

        BlockPos position = new BlockPos(spawnLoc.getBlockX(), spawnLoc.getBlockZ());

        Set<BlockPos> structurePositions = spawnedStructures.get(data.id());
        if (!structurePositions.add(position)) {
            return false;
        }

        boolean distCheckPassed = canSpawnAtExcludingPosition(data, spawnLoc.getBlockX(), spawnLoc.getBlockZ(), position)
                && !isTooCloseToOtherStructures(spawnLoc, data.id());

        if (!distCheckPassed) {
            structurePositions.remove(position);
            return false;
        }

        if (spawnStructure(data, spawnLoc, random)) {
            if (isFirstSpawnInBiome) {
                alreadySpawnedBiomes.add(currentBiome);
                plugin.getLogger().info("First time spawn for " + data.id() + " in biome " + currentBiome);
            }

            plugin.getLogger().info("Spawned " + data.id() + " at " + position.x + ", " + position.z +
                    " (Chance: " + String.format("%.2f%%", effectiveChance * 100) + ")");
            return true;
        } else {
            structurePositions.remove(position);
            return false;
        }
    }

    private boolean isBiomeStable(World world, int x, int z, Biome targetBiome) {
        int offset = 24;

        if (world.getBiome(new Location(world, x + offset, 64, z)) != targetBiome) return false;
        if (world.getBiome(new Location(world, x - offset, 64, z)) != targetBiome) return false;
        if (world.getBiome(new Location(world, x, 64, z + offset)) != targetBiome) return false;
        return world.getBiome(new Location(world, x, 64, z - offset)) == targetBiome;
    }

    private Location findValidSpawnLocation(World world, StructureData data, int x, int z) {
        Location testLoc = new Location(world, x, 64, z);
        if (!data.biomes().isEmpty() && !data.biomes().contains(world.getBiome(testLoc))) {
            return null;
        }

        // Для SURFACE робимо одну комплексну перевірку
        if (data.placementType() == StructurePlacementType.SURFACE) {
            return findFlatSurfaceLocation(world, x, z);
        }

        // Інші типи розміщення
        return switch (data.placementType()) {
            case LIQUID_SURFACE -> findLiquidSurfaceLocation(world, x, z);
            case SKY -> findSkyLocation(world, x, z, new Random());
            default -> new Location(world, x, world.getHighestBlockYAt(x, z), z);
        };
    }

    private Location findFlatSurfaceLocation(World world, int centerX, int centerZ) {
        List<Integer> ys = new ArrayList<>();
        int stride = GenerationConfig.SAMPLE_STRIDE;
        int radius = GenerationConfig.CHECK_RADIUS;

        // Семплюємо Y координати один раз
        for (int dx = -radius; dx <= radius; dx += stride) {
            for (int dz = -radius; dz <= radius; dz += stride) {
                int y = findSupportingY(world, centerX + dx, centerZ + dz);
                ys.add(y);
            }
        }

        if (ys.isEmpty()) {
            return null;
        }

        // Сортуємо і беремо медіану
        Collections.sort(ys);
        int medianY = ys.get(ys.size() / 2);
        int minY = ys.get(0);
        int maxY = ys.get(ys.size() - 1);

        // Одна проста перевірка: різниця висот не більше 3 блоків
        if (maxY - minY > GenerationConfig.MAX_HEIGHT_DIFFERENCE) {
            return null;
        }

        return new Location(world, centerX, medianY, centerZ);
    }

    private boolean canSpawnAt(StructureData data, int centerX, int centerZ) {
        return isMinimumDistanceRespected(
                centerX,
                centerZ,
                data.minDistance(),
                (id, positions) -> id.equals(data.id())
        );
    }

    private boolean canSpawnAtExcludingPosition(StructureData data, int centerX, int centerZ, BlockPos excludePos) {
        long minDistSq = (long) data.minDistance() * (long) data.minDistance();

        Set<BlockPos> positions = spawnedStructures.get(data.id());
        for (BlockPos pos : positions) {
            // Пропускаємо поточну зарезервовану позицію
            if (pos.equals(excludePos)) {
                continue;
            }
            if (calculateSquaredDistance(centerX, centerZ, pos.x, pos.z) < minDistSq) {
                return false;
            }
        }
        return true;
    }

    private boolean isTooCloseToOtherStructures(Location location, String currentStructureId) {
        return !isMinimumDistanceRespected(
                location.getBlockX(),
                location.getBlockZ(),
                GenerationConfig.MIN_DISTANCE_TO_ANY_STRUCTURE,
                (id, positions) -> !id.equals(currentStructureId)
        );
    }

    private boolean isMinimumDistanceRespected(int x, int z, int minDistance,
                                               BiPredicate<String, Set<BlockPos>> positionFilter) {
        long minDistSq = (long) minDistance * (long) minDistance;

        for (Map.Entry<String, Set<BlockPos>> entry : spawnedStructures.entrySet()) {
            if (!positionFilter.test(entry.getKey(), entry.getValue())) {
                continue;
            }

            for (BlockPos pos : entry.getValue()) {
                if (calculateSquaredDistance(x, z, pos.x, pos.z) < minDistSq) {
                    return false;
                }
            }
        }
        return true;
    }

    private long calculateSquaredDistance(int x1, int z1, int x2, int z2) {
        long dx = x1 - x2;
        long dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private int findSupportingY(World world, int x, int z) {
        int startY = world.getHighestBlockYAt(x, z);
        int minY = Math.max(world.getMinHeight(), startY - GenerationConfig.MAX_SCAN_DEPTH);

        for (int y = startY; y >= minY; y--) {
            Material below = world.getBlockAt(x, y - 1, z).getType();
            if (isSolidForSupport(below)) {
                return y;
            }
        }

        return startY;
    }

    private boolean isSolidForSupport(Material m) {
        return switch (m) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, MYCELIUM,
                 ROOTED_DIRT, DIRT_PATH, CLAY, MUD, MOSS_BLOCK, SNOW_BLOCK, POWDER_SNOW, ICE, PACKED_ICE, BLUE_ICE,
                 SAND, RED_SAND, GRAVEL, SANDSTONE, RED_SANDSTONE, STONE, DEEPSLATE, ANDESITE, GRANITE, DIORITE, TUFF,
                 CALCITE, DRIPSTONE_BLOCK -> true;
            default -> m.name().contains("TERRACOTTA") ||
                    m.name().contains("CONCRETE");
        };
    }

    private Location findLiquidSurfaceLocation(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Block block = world.getBlockAt(x, y - 1, z);

        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            return new Location(world, x, y, z);
        }

        return null;
    }

    private Location findSkyLocation(World world, int x, int z, Random random) {
        int y = 150 + random.nextInt(50);
        return new Location(world, x, y, z);
    }

    private boolean spawnStructure(StructureData data, Location location, Random random) {
        try {
            StructureRotation rotation = getRandomRotation(random);
            Location startLocation = calculateStartLocationForCenter(data.structure(), location, rotation);

            data.structure().place(
                    startLocation,
                    true,
                    rotation,
                    org.bukkit.block.structure.Mirror.NONE,
                    -1,
                    1.0f,
                    random
            );

            scheduleLootGeneration(data, location);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place structure " + data.id() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private Location calculateStartLocationForCenter(org.bukkit.structure.Structure structure, Location center, StructureRotation rotation) {
        org.bukkit.util.BlockVector size = structure.getSize();

        double sizeX = size.getX();
        double sizeZ = size.getZ();

        if (rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90) {
            double temp = sizeX;
            sizeX = sizeZ;
            sizeZ = temp;
        }

        return center.clone().subtract(sizeX / 2.0, 0, sizeZ / 2.0);
    }

    private StructureRotation getRandomRotation(Random random) {
        StructureRotation[] rotations = StructureRotation.values();
        return rotations[random.nextInt(rotations.length)];
    }

    private void scheduleLootGeneration(StructureData data, Location location) {
        if (data.lootTable() == null || data.lootTable().items().isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Processing loot for structure " + data.id() +
                    " with " + data.lootTable().items().size() + " loot items");
            lootGenerationService.processStructureLoot(data, location);
        }, GenerationConfig.LOOT_GENERATION_DELAY_TICKS);
    }

    public Set<String> getStructureIds() {
        return structures.keySet();
    }

    public boolean placeStructureManually(String id, Location location) {
        StructureData data = structures.get(id);
        if (data == null) {
            return false;
        }

        BlockPos position = new BlockPos(location.getBlockX(), location.getBlockZ());

        // Для ручного розміщення теж резервуємо позицію
        if (!spawnedStructures.get(data.id()).add(position)) {
            return false;
        }

        if (spawnStructure(data, location, new Random())) {
            return true;
        } else {
            spawnedStructures.get(data.id()).remove(position);
            return false;
        }
    }

    public void clearSpawnHistory(String structureId) {
        if (spawnedStructures.containsKey(structureId)) {
            spawnedStructures.get(structureId).clear();
            spawnedBiomes.get(structureId).clear();
            plugin.getLogger().info("Cleared spawn history for " + structureId);
        }
    }

    public void clearAllSpawnHistory() {
        spawnedStructures.values().forEach(Set::clear);
        spawnedBiomes.values().forEach(Set::clear);
        plugin.getLogger().info("Cleared all spawn history");
    }

    public Map<String, Integer> getSpawnCounts() {
        Map<String, Integer> counts = new HashMap<>();
        spawnedStructures.forEach((id, coords) -> counts.put(id, coords.size()));
        return counts;
    }

    private static class BlockPos {
        final int x;
        final int z;

        BlockPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos that = (BlockPos) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}