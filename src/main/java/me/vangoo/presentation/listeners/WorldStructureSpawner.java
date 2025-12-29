package me.vangoo.presentation.listeners;

import me.vangoo.application.services.StructureService;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.*;
import java.util.logging.Logger;

/**
 * Spawns structures randomly when chunks load
 */
public class WorldStructureSpawner implements Listener {
    private static final Logger LOGGER = Logger.getLogger(WorldStructureSpawner.class.getName());

    private final StructureService structureService;
    private final Map<String, SpawnConfig> spawnConfigs;
    private final Random random = new Random();
    private final Set<String> processedChunks = new HashSet<>();

    public WorldStructureSpawner(StructureService structureService, Map<String, SpawnConfig> spawnConfigs) {
        this.structureService = structureService;
        this.spawnConfigs = spawnConfigs;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        // Ignore already processed chunks
        String chunkKey = getChunkKey(chunk);
        if (processedChunks.contains(chunkKey)) {
            return;
        }

        // Skip if not a new chunk
        if (!event.isNewChunk()) {
            return;
        }

        processedChunks.add(chunkKey);

        // Try spawning structures
        trySpawnStructures(chunk);
    }

    private void trySpawnStructures(Chunk chunk) {
        World world = chunk.getWorld();

        for (Map.Entry<String, SpawnConfig> entry : spawnConfigs.entrySet()) {
            String structureId = entry.getKey();
            SpawnConfig config = entry.getValue();

            // Check world whitelist
            if (!config.worlds().isEmpty() && !config.worlds().contains(world.getName())) {
                continue;
            }

            // Roll spawn chance
            if (random.nextDouble() > config.chance()) {
                continue;
            }

            // Get spawn location
            Location location = findSpawnLocation(chunk, config);

            // Generate structure
            boolean success = structureService.placeStructure(structureId, location);

            if (success) {
                LOGGER.info(String.format("Spawned structure '%s' at (%d, %d, %d) in world '%s'",
                        structureId,
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ(),
                        world.getName()));
            }
        }
    }

    private Location findSpawnLocation(Chunk chunk, SpawnConfig config) {
        World world = chunk.getWorld();

        // Random position in chunk
        int x = chunk.getX() * 16 + random.nextInt(16);
        int z = chunk.getZ() * 16 + random.nextInt(16);

        // Find suitable Y level
        int y = switch (config.placement()) {
            case SURFACE -> world.getHighestBlockYAt(x, z) + 1;
            case UNDERGROUND -> config.minY() + random.nextInt(config.maxY() - config.minY() + 1);
            case RANDOM -> random.nextInt(world.getMaxHeight());
        };

        // Clamp Y to world bounds
        y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight(), y));

        return new Location(world, x, y, z);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    /**
     * Spawn configuration for structures
     */
    public record SpawnConfig(
            double chance,
            PlacementType placement,
            int minY,
            int maxY,
            List<String> worlds
    ) {
        public SpawnConfig {
            if (chance < 0 || chance > 1) {
                throw new IllegalArgumentException("Chance must be between 0 and 1");
            }
            worlds = worlds != null ? List.copyOf(worlds) : List.of();
        }
    }

    public enum PlacementType {
        SURFACE,      // На поверхні
        UNDERGROUND,  // Під землею
        RANDOM        // Випадково
    }
}