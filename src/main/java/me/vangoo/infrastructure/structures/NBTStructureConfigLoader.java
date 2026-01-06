package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.StructureData;
import me.vangoo.domain.valueobjects.StructurePlacementType;
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
    private LootTableData globalLootTable;

    public NBTStructureConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public LootTableData getGlobalLootTable() {
        return globalLootTable;
    }

    public Map<String, StructureData> loadAllStructures() {
        Map<String, StructureData> structures = new HashMap<>();
        File configFile = new File(plugin.getDataFolder(), "structures.yml");

        if (!configFile.exists()) {
            plugin.saveResource("structures.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Спочатку завантажуємо глобальну loot table
        loadGlobalLootTable(config);

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

    private void loadGlobalLootTable(YamlConfiguration config) {
        ConfigurationSection globalSection = config.getConfigurationSection("global_loot");

        if (globalSection == null) {
            plugin.getLogger().warning("No 'global_loot' section found in structures.yml. Using empty loot table.");
            globalLootTable = new LootTableData(new ArrayList<>(), 3, 8);
            return;
        }

        ConfigurationSection itemsSection = globalSection.getConfigurationSection("items");
        List<LootItem> items = parseLootItems(itemsSection, "global_loot");

        int minItems = globalSection.getInt("min_items", 3);
        int maxItems = globalSection.getInt("max_items", 8);

        if (minItems < 0) minItems = 0;
        if (maxItems < minItems) maxItems = minItems;

        globalLootTable = new LootTableData(items, minItems, maxItems);

        plugin.getLogger().info("Loaded global loot table with " + items.size() +
                " items (min=" + minItems + ", max=" + maxItems + ")");
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

        int minDistance = spawnSection.getInt("min_distance", 500);
        if (minDistance < 0) {
            plugin.getLogger().warning("Structure '" + id + "' has invalid min_distance: " + minDistance + ". Using default 500");
            minDistance = 500;
        }

        String placementTypeStr = spawnSection.getString("placement_type", "SURFACE");
        StructurePlacementType placementType;
        try {
            placementType = StructurePlacementType.valueOf(placementTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Structure '" + id + "' has invalid placement_type: " + placementTypeStr + ". Using SURFACE");
            placementType = StructurePlacementType.SURFACE;
        }

        // Читаємо налаштування лута (можна перевизначити глобальні)
        LootTableData lootTable = parseLootConfig(section, id);

        plugin.getLogger().info("  Structure '" + id + "' config: chance=" + chance +
                ", minDistance=" + minDistance + ", placementType=" + placementType +
                ", lootItems=" + lootTable.items().size());

        return new StructureData(id, structure, biomes, chance, minDistance, placementType, lootTable);
    }

    private LootTableData parseLootConfig(ConfigurationSection section, String structureId) {
        ConfigurationSection lootSection = section.getConfigurationSection("loot");

        if (lootSection == null) {
            // Використовуємо глобальну таблицю
            plugin.getLogger().info("Structure '" + structureId + "' using global loot table");
            return globalLootTable;
        }

        // Якщо є custom items для цієї структури
        ConfigurationSection itemsSection = lootSection.getConfigurationSection("items");
        List<LootItem> items;

        if (itemsSection != null) {
            // Використовуємо кастомні items
            items = parseLootItems(itemsSection, structureId);
            plugin.getLogger().info("Structure '" + structureId + "' using custom loot table with " + items.size() + " items");
        } else {
            // Використовуємо items з глобальної таблиці
            items = globalLootTable.items();
            plugin.getLogger().info("Structure '" + structureId + "' using global loot items");
        }

        // Читаємо min/max items (можуть бути перевизначені)
        Integer minItems = lootSection.isSet("min_items") ? lootSection.getInt("min_items") : null;
        Integer maxItems = lootSection.isSet("max_items") ? lootSection.getInt("max_items") : null;

        if (minItems == null && maxItems == null) {
            // Використовуємо значення з глобальної таблиці
            return new LootTableData(items, globalLootTable.minItems(), globalLootTable.maxItems());
        }

        int min = minItems != null ? Math.max(0, minItems) : globalLootTable.minItems();
        int max = maxItems != null ? Math.max(min, maxItems) : globalLootTable.maxItems();

        return new LootTableData(items, min, max);
    }

    private Structure loadStructureFromFile(File nbtFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(nbtFile)) {
            return Bukkit.getStructureManager().loadStructure(fis);
        } catch (IOException e) {
            throw new IOException("Failed to load structure from file " + nbtFile.getName() + ": " + e.getMessage(), e);
        }
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

    private List<LootItem> parseLootItems(ConfigurationSection section, String tableKey) {
        List<LootItem> items = new ArrayList<>();

        if (section == null) {
            plugin.getLogger().warning("No 'items' section for loot table: " + tableKey);
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