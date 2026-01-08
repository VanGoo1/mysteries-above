package me.vangoo.infrastructure.structures;

import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/**
 * Завантажувач конфігурації loot table з global_loot.yml
 */
public class LootTableConfigLoader {

    private final Plugin plugin;
    private LootTableData globalLootTable;

    public LootTableConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public LootTableData getGlobalLootTable() {
        return globalLootTable;
    }

    /**
     * Завантажує глобальну loot table з конфігурації
     */
    public void loadLootTable() {
        File configFile = new File(plugin.getDataFolder(), "global_loot.yml");

        if (!configFile.exists()) {
            plugin.saveResource("global_loot.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        loadGlobalLootTable(config);
    }

    private void loadGlobalLootTable(YamlConfiguration config) {
        ConfigurationSection globalSection = config.getConfigurationSection("global_loot");

        if (globalSection == null) {
            plugin.getLogger().warning("No 'global_loot' section found in global_loot.yml. Using empty loot table.");
            globalLootTable = new LootTableData(new ArrayList<>(), 1, 2);
            return;
        }

        ConfigurationSection itemsSection = globalSection.getConfigurationSection("items");
        List<LootItem> items = parseLootItems(itemsSection);

        int minItems = globalSection.getInt("min_items", 1);
        int maxItems = globalSection.getInt("max_items", 2);

        if (minItems < 0) minItems = 0;
        if (maxItems < minItems) maxItems = minItems;

        globalLootTable = new LootTableData(items, minItems, maxItems);

        plugin.getLogger().info("Loaded global loot table with " + items.size() +
                " items (min=" + minItems + ", max=" + maxItems + ")");
    }

    private List<LootItem> parseLootItems(ConfigurationSection section) {
        List<LootItem> items = new ArrayList<>();

        if (section == null) {
            plugin.getLogger().warning("No 'items' section in global_loot");
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
                plugin.getLogger().warning("Missing 'item_id' for item '" + key + "'");
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

        plugin.getLogger().info("Loaded " + items.size() + " loot items from config");
        return items;
    }
}