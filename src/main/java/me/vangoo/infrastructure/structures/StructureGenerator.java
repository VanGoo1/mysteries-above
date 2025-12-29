package me.vangoo.infrastructure.structures;

import me.vangoo.application.services.LootGenerationService;
import me.vangoo.application.services.StructureService;
import me.vangoo.domain.valueobjects.LootTable;
import me.vangoo.domain.valueobjects.Structure;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Infrastructure: Generates structures in world with loot
 */
public class StructureGenerator extends BlockPopulator {
    private final Plugin plugin;
    private final StructureService structureService;
    private final LootGenerationService lootService;
    private final Set<String> processedChunks;
    private final NamespacedKey lootTagKey;

    public StructureGenerator(Plugin plugin,
                              StructureService structureService,
                              LootGenerationService lootService
    ) {
        this.plugin = plugin;
        this.structureService = structureService;
        this.lootService = lootService;
        this.processedChunks = new HashSet<>();
        this.lootTagKey = new NamespacedKey(plugin, "structure_loot_tag");
    }

    public NamespacedKey getLootTagKey() {
        return lootTagKey;
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        String chunkKey = getChunkKey(chunk);

        // Skip if already processed
        if (processedChunks.contains(chunkKey)) {
            return;
        }

        processedChunks.add(chunkKey);

        // Try to spawn structures
        for (Structure structure : structureService.getAllStructures()) {
            if (!structure.spawnConfig().shouldSpawn()) {
                continue;
            }

            Location spawnLocation = findSpawnLocation(chunk, structure);
            if (spawnLocation == null) {
                continue;
            }

            // Place structure with random rotation
            if (structureService.placeStructure(structure.id(), spawnLocation)) {
                // Fill loot chests after short delay
                scheduleChestFilling(structure, spawnLocation);
                break; // Only one structure per chunk
            }
        }
    }

    /**
     * Find valid spawn location in chunk
     */
    private Location findSpawnLocation(Chunk chunk, Structure structure) {
        World world = chunk.getWorld();

        // Get center of chunk
        int x = (chunk.getX() << 4) + 8;
        int z = (chunk.getZ() << 4) + 8;

        // Find ground level
        int y = world.getHighestBlockYAt(x, z);

        Location location = new Location(world, x, y, z);

        // Check biome
        if (!structure.spawnConfig().isBiomeAllowed(world.getBiome(location))) {
            return null;
        }

        return location;
    }

    /**
     * Schedule chest filling after structure placement
     */
    private void scheduleChestFilling(Structure structure, Location baseLocation) {
        if (!structure.hasLootTables()) {
            return;
        }

        // Fill chests 2 seconds later (after structure fully generates)
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> fillStructureChests(structure, baseLocation),
                40L
        );
    }

    /**
     * Fill all chests in structure with loot
     */
    private void fillStructureChests(Structure structure, Location baseLocation) {
        World world = baseLocation.getWorld();

        // Search for chests in structure area (32x32x32)
        int searchRadius = 32;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = world.getBlockAt(
                            baseLocation.getBlockX() + x,
                            baseLocation.getBlockY() + y,
                            baseLocation.getBlockZ() + z
                    );

                    if (block.getState() instanceof Chest chest) {
                        fillChest(chest, structure);
                    }
                }
            }
        }
    }

    /**
     * Fill chest with appropriate loot table
     */
    private void fillChest(Chest chest, Structure structure) {
        var container = chest.getPersistentDataContainer();

        // 2. Шукаємо наш тег (наприклад, "loot", "rare_treasure")
        if (!container.has(lootTagKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            return; // Скриня без тегу - ігноруємо
        }

        String tag = container.get(lootTagKey, org.bukkit.persistence.PersistentDataType.STRING);

        // 3. Шукаємо відповідну таблицю луту в конфігу структури
        structure.lootTables().stream()
                .filter(table -> table.chestTag().equals(tag))
                .findFirst()
                .ifPresent(lootTable -> {
                    // 4. Очищаємо скриню від мітки (щоб не заповнювати двічі, якщо треба) і заповнюємо лутом
                    container.remove(lootTagKey);
                    chest.update();

                    lootService.fillChest(chest.getBlock(), lootTable);
                });
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}