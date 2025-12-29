package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.LootTable;
import me.vangoo.domain.valueobjects.Structure;
import me.vangoo.domain.valueobjects.StructureSpawnConfig;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Infrastructure: Loads structure configurations from YAML
 */
public class StructureConfigLoader {
    private static final Logger LOGGER = Logger.getLogger(StructureConfigLoader.class.getName());
    private static final String CONFIG_FILE = "structures.yml";
    private static final String STRUCTURES_KEY = "structures";

    private final Plugin plugin;

    public StructureConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load structures from configuration
     */
    public Map<String, Structure> loadStructures() {
        Map<String, Structure> structures = new HashMap<>();

        FileConfiguration config = loadConfiguration();
        if (config == null) {
            LOGGER.warning("Failed to load structures.yml");
            return structures;
        }

        ConfigurationSection structuresSection = config.getConfigurationSection(STRUCTURES_KEY);
        if (structuresSection == null) {
            LOGGER.warning("No 'structures' section in structures.yml");
            return structures;
        }

        int loaded = 0;
        int failed = 0;

        for (String structureId : structuresSection.getKeys(false)) {
            try {
                Structure structure = parseStructure(
                        structureId,
                        structuresSection.getConfigurationSection(structureId)
                );
                structures.put(structureId, structure);
                loaded++;
            } catch (Exception e) {
                failed++;
                LOGGER.warning("Failed to load structure '" + structureId + "': " + e.getMessage());
            }
        }

        LOGGER.info("Structures loaded: " + loaded + (failed > 0 ? " (" + failed + " failed)" : ""));
        return structures;
    }

    private FileConfiguration loadConfiguration() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);

        // Create default if not exists
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    private Structure parseStructure(String id, ConfigurationSection section) {
        String file = section.getString("file");
        if (file == null) {
            throw new IllegalArgumentException("Missing 'file' field");
        }

        StructureSpawnConfig spawnConfig = parseSpawnConfig(
                section.getConfigurationSection("spawn")
        );

        List<LootTable> lootTables = parseLootTables(
                section.getConfigurationSection("loot_tables")
        );

        return new Structure(id, file, spawnConfig, lootTables);
    }

    private StructureSpawnConfig parseSpawnConfig(ConfigurationSection section) {
        if (section == null) {
            return new StructureSpawnConfig(List.of(), 0.01, 1000);
        }

        List<String> biomeNames = section.getStringList("biomes");
        List<Biome> biomes = biomeNames.stream()
                .map(name -> {
                    try {
                        return Biome.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid biome: " + name);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        double chance = section.getDouble("chance", 0.01);
        int minDistance = section.getInt("min_distance", 1000);

        return new StructureSpawnConfig(biomes, chance, minDistance);
    }

    private List<LootTable> parseLootTables(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }

        List<LootTable> tables = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            ConfigurationSection tableSection = section.getConfigurationSection(key);
            if (tableSection == null) continue;

            String chestTag = tableSection.getString("chest_tag", "loot");
            List<LootTable.LootEntry> entries = parseEntries(
                    tableSection.getConfigurationSection("items")
            );

            tables.add(new LootTable(chestTag, entries));
        }

        return tables;
    }

    private List<LootTable.LootEntry> parseEntries(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }

        List<LootTable.LootEntry> entries = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) continue;

            try {
                String itemId = itemSection.getString("item_id");
                int weight = itemSection.getInt("weight", 1);
                int amountMin = itemSection.getInt("amount_min", 1);
                int amountMax = itemSection.getInt("amount_max", 1);

                entries.add(new LootTable.LootEntry(itemId, weight, amountMin, amountMax));
            } catch (Exception e) {
                LOGGER.warning("Failed to parse loot entry '" + key + "': " + e.getMessage());
            }
        }

        return entries;
    }
}