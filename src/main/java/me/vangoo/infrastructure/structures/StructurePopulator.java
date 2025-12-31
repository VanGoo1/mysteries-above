package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.StructureData;
import org.bukkit.*;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class StructurePopulator extends BlockPopulator {

    private final Plugin plugin;
    private final Map<String, StructureData> structures;
    private final LootGenerationService lootGenerationService;
    private final Set<String> processedChunks = new HashSet<>();
    private final Random random = new Random();

    public StructurePopulator(Plugin plugin,
                              NBTStructureConfigLoader configLoader,
                              LootGenerationService lootGenerationService) {
        this.plugin = plugin;
        this.lootGenerationService = lootGenerationService;
        // Завантажуємо структури при ініціалізації
        this.structures = configLoader.loadAllStructures();
        plugin.getLogger().info("StructurePopulator initialized with " + structures.size() + " structures.");
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        String chunkKey = world.getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (processedChunks.contains(chunkKey)) return;
        processedChunks.add(chunkKey);

        for (StructureData data : structures.values()) {
            // Перевірка шансу
            if (random.nextDouble() > data.spawnChance()) continue;

            // Визначення координат
            int x = (chunk.getX() << 4) + 8;
            int z = (chunk.getZ() << 4) + 8;
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x, y, z);

            // Перевірка біому
            if (!data.biomes().isEmpty() && !data.biomes().contains(world.getBiome(loc))) {
                continue;
            }

            spawnStructure(data, loc);
            // Спавнимо тільки одну структуру на чанк (якщо це бажана поведінка)
            break;
        }
    }

    private void spawnStructure(StructureData data, Location location) {
        try {
            // 1. Фізичне розміщення блоків структури
            data.structure().place(
                    location,
                    true,
                    StructureRotation.values()[random.nextInt(4)],
                    org.bukkit.block.structure.Mirror.NONE,
                    -1,
                    1.0f,
                    random
            );

            // 2. Планування генерації луту (відкладено, щоб переконатися, що блоки оновилися)
            if (!data.lootTables().isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                                lootGenerationService.processStructureLoot(data, location),
                        40L // 2 секунди затримки
                );
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place structure " + data.id() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Set<String> getStructureIds() {
        return structures.keySet();
    }

    public boolean placeStructureManually(String id, Location location) {
        StructureData data = structures.get(id);
        if (data == null) return false;

        spawnStructure(data, location);
        return true;
    }
}