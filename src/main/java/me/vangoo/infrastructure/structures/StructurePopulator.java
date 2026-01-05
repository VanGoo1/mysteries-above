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
import java.util.function.BiPredicate;

public class StructurePopulator extends BlockPopulator {

    private final Plugin plugin;
    private final Map<String, StructureData> structures;
    private final LootGenerationService lootGenerationService;
    private final Map<String, Set<BlockPos>> spawnedStructures = new ConcurrentHashMap<>();

    // Конфігурація генерації
    private static final class GenerationConfig {
        static final int TERRAIN_FLATNESS_RADIUS = 8;
        static final int MAX_HEIGHT_DIFFERENCE = 3;
        static final int MIN_DISTANCE_TO_ANY_STRUCTURE = 200;
        static final int FOOTPRINT_RADIUS_DEFAULT = 6;
        static final int FILL_UNDER_MAX_DEPTH = 6;
        static final int SAMPLE_STRIDE = 1;
        static final int REGION_SIZE = 8;
        static final int MAX_SCAN_DEPTH = 40;
        static final double MAX_UNSUITABLE_SAMPLES_RATIO = 0.4;
        static final long LOOT_GENERATION_DELAY_TICKS = 60L;
    }

    public StructurePopulator(Plugin plugin,
                              NBTStructureConfigLoader configLoader,
                              LootGenerationService lootGenerationService) {
        this.plugin = plugin;
        this.lootGenerationService = lootGenerationService;
        this.structures = configLoader.loadAllStructures();

        structures.keySet().forEach(id -> spawnedStructures.put(id, ConcurrentHashMap.newKeySet()));
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
        if (random.nextDouble() > data.spawnChance()) {
            return false;
        }

        int x = (chunk.getX() << 4) + random.nextInt(16);
        int z = (chunk.getZ() << 4) + random.nextInt(16);

        if (!canSpawnAt(data, x, z)) {
            return false;
        }

        Location spawnLoc = findValidSpawnLocation(world, data, x, z);
        if (spawnLoc == null) {
            return false;
        }

        if (isTooCloseToOtherStructures(spawnLoc, data.id())) {
            return false;
        }

        if (spawnStructure(data, spawnLoc, random)) {
            registerSpawnedStructure(data, spawnLoc);
            return true;
        }

        return false;
    }

    private Location findValidSpawnLocation(World world, StructureData data, int x, int z) {
        int footprintRadius = GenerationConfig.FOOTPRINT_RADIUS_DEFAULT;
        Location spawnLoc = findSurfaceLocationBetter(world, x, z, footprintRadius);

        if (spawnLoc == null) {
            return null;
        }

        if (!data.biomes().isEmpty() && !data.biomes().contains(world.getBiome(spawnLoc))) {
            return null;
        }

        if (data.placementType() == StructurePlacementType.SURFACE) {
            if (!isTerrainFlatAround(world, spawnLoc.getBlockX(), spawnLoc.getBlockZ(),
                    GenerationConfig.TERRAIN_FLATNESS_RADIUS)) {
                return null;
            }
        }

        return spawnLoc;
    }

    private void registerSpawnedStructure(StructureData data, Location location) {
        spawnedStructures.get(data.id()).add(new BlockPos(location.getBlockX(), location.getBlockZ()));
        plugin.getLogger().info(String.format("Spawned structure %s at %d %d %d",
                data.id(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    // ==================== Перевірки відстані (рефакторинг дублювання) ====================

    private boolean canSpawnAt(StructureData data, int centerX, int centerZ) {
        return isMinimumDistanceRespected(
                centerX,
                centerZ,
                data.minDistance(),
                (id, positions) -> id.equals(data.id())
        );
    }

    private boolean isTooCloseToOtherStructures(Location location, String currentStructureId) {
        return !isMinimumDistanceRespected(
                location.getBlockX(),
                location.getBlockZ(),
                GenerationConfig.MIN_DISTANCE_TO_ANY_STRUCTURE,
                (id, positions) -> !id.equals(currentStructureId)
        );
    }

    /**
     * Універсальний метод перевірки мінімальної відстані
     */
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

    // ==================== Пошук поверхні (рефакторинг дублювання) ====================

    private Location findSurfaceLocationBetter(World world, int centerX, int centerZ, int footprintRadius) {
        List<Integer> ys = sampleFootprintYCoordinates(world, centerX, centerZ, footprintRadius);

        if (ys.isEmpty()) {
            return null;
        }

        TerrainAnalysis analysis = analyzeTerrainSamples(ys);

        if (!analysis.isSuitable()) {
            return null;
        }

        return new Location(world, centerX, analysis.medianY, centerZ);
    }

    private boolean isTerrainFlatAround(World world, int centerX, int centerZ, int radius) {
        int centerY = findSupportingY(world, centerX, centerZ);
        List<Integer> ys = sampleFootprintYCoordinates(world, centerX, centerZ, radius);

        return ys.stream().allMatch(y ->
                Math.abs(y - centerY) <= GenerationConfig.MAX_HEIGHT_DIFFERENCE
        );
    }

    /**
     * Універсальний метод семплування Y координат у footprint
     */
    private List<Integer> sampleFootprintYCoordinates(World world, int centerX, int centerZ, int radius) {
        List<Integer> ys = new ArrayList<>();
        int stride = Math.max(1, GenerationConfig.SAMPLE_STRIDE);

        for (int dx = -radius; dx <= radius; dx += stride) {
            for (int dz = -radius; dz <= radius; dz += stride) {
                int y = findSupportingY(world, centerX + dx, centerZ + dz);
                ys.add(y);
            }
        }

        return ys;
    }

    private TerrainAnalysis analyzeTerrainSamples(List<Integer> ys) {
        Collections.sort(ys);
        int median = ys.get(ys.size() / 2);
        int min = ys.get(0);
        int max = ys.get(ys.size() - 1);

        if (max - min > GenerationConfig.MAX_HEIGHT_DIFFERENCE) {
            return TerrainAnalysis.unsuitable();
        }

        long unsuitableCount = ys.stream()
                .filter(y -> Math.abs(y - median) > GenerationConfig.MAX_HEIGHT_DIFFERENCE)
                .count();

        boolean tooManyUnsuitableSamples =
                unsuitableCount > ys.size() * GenerationConfig.MAX_UNSUITABLE_SAMPLES_RATIO;

        return tooManyUnsuitableSamples
                ? TerrainAnalysis.unsuitable()
                : TerrainAnalysis.suitable(median);
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
        switch (m) {
            case GRASS_BLOCK:
            case DIRT:
            case COARSE_DIRT:
            case PODZOL:
            case MYCELIUM:
            case STONE:
            case DEEPSLATE:
            case SAND:
            case RED_SAND:
            case GRAVEL:
            case SNOW_BLOCK:
            case POWDER_SNOW:
                return true;
            default:
                return m.name().contains("TERRACOTTA");
        }
    }

    // ==================== Альтернативні типи розміщення ====================

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

    // ==================== Спавн структури ====================

    private boolean spawnStructure(StructureData data, Location location, Random random) {
        try {
            StructureRotation rotation = getRandomRotation(random);

            data.structure().place(
                    location,
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

    // ==================== Публічні методи управління ====================

    public Set<String> getStructureIds() {
        return structures.keySet();
    }

    public boolean placeStructureManually(String id, Location location) {
        StructureData data = structures.get(id);
        if (data == null) {
            return false;
        }
        return spawnStructure(data, location, new Random());
    }

    public void clearSpawnHistory(String structureId) {
        if (spawnedStructures.containsKey(structureId)) {
            spawnedStructures.get(structureId).clear();
            plugin.getLogger().info("Cleared spawn history for " + structureId);
        }
    }

    public void clearAllSpawnHistory() {
        spawnedStructures.values().forEach(Set::clear);
        plugin.getLogger().info("Cleared all spawn history");
    }

    public Map<String, Integer> getSpawnCounts() {
        Map<String, Integer> counts = new HashMap<>();
        spawnedStructures.forEach((id, coords) -> counts.put(id, coords.size()));
        return counts;
    }

    // ==================== Допоміжні класи ====================

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

    private static class TerrainAnalysis {
        final boolean suitable;
        final int medianY;

        private TerrainAnalysis(boolean suitable, int medianY) {
            this.suitable = suitable;
            this.medianY = medianY;
        }

        static TerrainAnalysis suitable(int medianY) {
            return new TerrainAnalysis(true, medianY);
        }

        static TerrainAnalysis unsuitable() {
            return new TerrainAnalysis(false, 0);
        }

        boolean isSuitable() {
            return suitable;
        }
    }
}