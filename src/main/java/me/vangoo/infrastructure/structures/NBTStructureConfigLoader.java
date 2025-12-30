package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.StructureData;
import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class NBTStructureConfigLoader {

    private final Plugin plugin;

    public NBTStructureConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, StructureData> loadAllStructures() {
        Map<String, StructureData> structures = new HashMap<>();
        File configFile = new File(plugin.getDataFolder(), "structures.yml");

        if (!configFile.exists()) {
            plugin.saveResource("structures.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection structuresSection = config.getConfigurationSection("structures");

        if (structuresSection == null) {
            plugin.getLogger().warning("No 'structures' section found in structures.yml");
            return structures;
        }

        for (String id : structuresSection.getKeys(false)) {
            try {
                StructureData data = loadStructureData(id, structuresSection.getConfigurationSection(id));
                structures.put(id, data);
                plugin.getLogger().info("Successfully loaded structure: " + id);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load structure '" + id + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + structures.size() + " structures total");
        return structures;
    }

    private StructureData loadStructureData(String id, ConfigurationSection section) throws Exception {
        if (section == null) {
            throw new IllegalArgumentException("Structure section is null for id: " + id);
        }

        // Читаємо файл NBT
        String fileName = section.getString("file");
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'file' field for structure: " + id);
        }

        File nbtFile = saveDefaultStructureIfNotExists(fileName);
        Structure structure = loadStructureFromFile(nbtFile);

        // Читаємо параметри спавну
        ConfigurationSection spawnSection = section.getConfigurationSection("spawn");
        if (spawnSection == null) {
            throw new IllegalArgumentException("Missing 'spawn' section for structure: " + id);
        }

        List<Biome> biomes = parseBiomes(spawnSection.getStringList("biomes"));
        if (biomes.isEmpty()) {
            plugin.getLogger().warning("Structure '" + id + "' has no valid biomes configured");
        }

        double chance = spawnSection.getDouble("chance", 0.01);
        if (chance <= 0 || chance > 1) {
            plugin.getLogger().warning("Structure '" + id + "' has invalid spawn chance: " + chance + ". Using default 0.01");
            chance = 0.01;
        }

        // Читаємо loot tables
        Map<String, LootTableData> lootTables = parseLootTables(section, id);

        return new StructureData(id, structure, biomes, chance, lootTables);
    }

    private Structure loadStructureFromFile(File nbtFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(nbtFile)) {
            Structure structure = Bukkit.getStructureManager().loadStructure(fis);
            if (structure == null) {
                throw new IOException("Structure loaded as null from file: " + nbtFile.getName());
            }
            return structure;
        } catch (IOException e) {
            throw new IOException("Failed to load structure from file " + nbtFile.getName() + ": " + e.getMessage(), e);
        }
    }

    private Map<String, LootTableData> parseLootTables(ConfigurationSection section, String structureId) {
        Map<String, LootTableData> lootTables = new HashMap<>();
        ConfigurationSection lootSection = section.getConfigurationSection("loot_tables");

        if (lootSection == null) {
            plugin.getLogger().info("Structure '" + structureId + "' has no loot tables configured");
            return lootTables;
        }

        for (String tableKey : lootSection.getKeys(false)) {
            ConfigurationSection tableSection = lootSection.getConfigurationSection(tableKey);
            if (tableSection == null) {
                plugin.getLogger().warning("Invalid loot table section: " + tableKey + " in structure: " + structureId);
                continue;
            }

            String chestTag = tableSection.getString("chest_tag");
            if (chestTag == null || chestTag.isEmpty()) {
                plugin.getLogger().warning("Missing 'chest_tag' for loot table '" + tableKey + "' in structure: " + structureId);
                continue;
            }

            List<LootItem> items = parseLootItems(tableSection.getConfigurationSection("items"), structureId, tableKey);
            if (items.isEmpty()) {
                plugin.getLogger().warning("Loot table '" + tableKey + "' in structure '" + structureId + "' has no valid items");
                continue;
            }

            // Read min/max items — підтримуємо різні стилі ключів
            Integer minItems = null;
            Integer maxItems = null;

            if (tableSection.isInt("min_items")) minItems = tableSection.getInt("min_items");
            else if (tableSection.isInt("minItems")) minItems = tableSection.getInt("minItems");
            else if (tableSection.isInt("Min")) minItems = tableSection.getInt("Min");

            if (tableSection.isInt("max_items")) maxItems = tableSection.getInt("max_items");
            else if (tableSection.isInt("maxItems")) maxItems = tableSection.getInt("maxItems");
            else if (tableSection.isInt("Max")) maxItems = tableSection.getInt("Max");

            LootTableData lootTable;
            if (minItems == null && maxItems == null) {
                // Нічого не задано — використовуємо конструктор з дефолтами (3..8)
                lootTable = new LootTableData(items);
                plugin.getLogger().info("Loaded loot table '" + tableKey + "' (tag='" + chestTag + "') using default min/max (3..8). Items: " + items.size());
            } else {
                // Якщо одне значення відсутнє — підставляємо зрозумілі дефолти
                int min = (minItems != null) ? Math.max(0, minItems) : 3;
                int max = (maxItems != null) ? Math.max(min, maxItems) : Math.max(min, 8);
                lootTable = new LootTableData(items, min, max);
                plugin.getLogger().info("Loaded loot table '" + tableKey + "' (tag='" + chestTag + "') min=" + min + " max=" + max + " Items: " + items.size());
            }

            lootTables.put(chestTag, lootTable);
        }

        return lootTables;
    }


    private File saveDefaultStructureIfNotExists(String fileName) throws IOException {
        File structureFolder = new File(plugin.getDataFolder(), "structures");
        if (!structureFolder.exists()) {
            if (!structureFolder.mkdirs()) {
                throw new IOException("Failed to create structures folder");
            }
        }

        File nbtFile = new File(structureFolder, fileName);

        if (!nbtFile.exists()) {
            String resourcePath = "structures/" + fileName;

            if (plugin.getResource(resourcePath) != null) {
                plugin.getLogger().info("Extracting default structure: " + fileName);
                plugin.saveResource(resourcePath, false);
            } else {
                throw new IOException("NBT file not found in plugin folder or resources: " + fileName);
            }
        }

        return nbtFile;
    }

    private List<Biome> parseBiomes(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }

        List<Biome> biomes = new ArrayList<>();
        for (String name : names) {
            try {
                Biome biome = Biome.valueOf(name.toUpperCase().replace(" ", "_"));
                biomes.add(biome);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid biome name: " + name);
            }
        }
        return biomes;
    }

    private List<LootItem> parseLootItems(ConfigurationSection section, String structureId, String tableKey) {
        List<LootItem> items = new ArrayList<>();

        if (section == null) {
            plugin.getLogger().warning("No 'items' section for loot table '" + tableKey + "' in structure: " + structureId);
            return items;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                plugin.getLogger().warning("Invalid item configuration: " + key);
                continue;
            }

            String itemId = itemSection.getString("item_id");
            if (itemId == null || itemId.isEmpty()) {
                plugin.getLogger().warning("Missing 'item_id' for item '" + key + "' in loot table '" + tableKey + "'");
                continue;
            }

            int weight = itemSection.getInt("weight", 1);
            int amountMin = itemSection.getInt("amount_min", 1);
            int amountMax = itemSection.getInt("amount_max", 1);

            // Валідація
            if (weight <= 0) {
                plugin.getLogger().warning("Invalid weight for item '" + key + "': " + weight + ". Using 1");
                weight = 1;
            }

            if (amountMin < 1) {
                plugin.getLogger().warning("Invalid amount_min for item '" + key + "': " + amountMin + ". Using 1");
                amountMin = 1;
            }

            if (amountMax < amountMin) {
                plugin.getLogger().warning("amount_max (" + amountMax + ") less than amount_min (" + amountMin + ") for item '" + key + "'. Setting equal");
                amountMax = amountMin;
            }

            items.add(new LootItem(itemId, weight, amountMin, amountMax));
        }

        return items;
    }
}