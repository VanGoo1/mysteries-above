package me.vangoo.infrastructure.items;

import me.vangoo.domain.valueobjects.CustomItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads custom items from custom-items.yml located inside plugin resources (JAR).
 * Updated for Minecraft 1.21.8 - supports String customModelData
 * Read-only — no files are written to disk.
 */
public class CustomItemConfigLoader {

    private static final String CONFIG_FILE = "custom-items.yml";
    private static final String CUSTOM_ITEMS_KEY = "custom-items";

    private final Plugin plugin;
    private final Logger logger;

    public CustomItemConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Load custom items from the resource inside the plugin JAR.
     *
     * @return map of item id -> CustomItem
     */
    public Map<String, CustomItem> loadItems() {
        Map<String, CustomItem> items = new HashMap<>();

        FileConfiguration config = loadConfigurationFromResource();
        if (config == null) {
            logger.warning(CONFIG_FILE + " resource not found inside plugin JAR; no custom items loaded.");
            return items;
        }

        ConfigurationSection itemsSection = config.getConfigurationSection(CUSTOM_ITEMS_KEY);
        if (itemsSection == null) {
            logger.warning("No '" + CUSTOM_ITEMS_KEY + "' section found in " + CONFIG_FILE + " (resource).");
            return items;
        }

        int loaded = 0;
        int failed = 0;

        for (String itemId : itemsSection.getKeys(false)) {
            try {
                CustomItem item = parseItem(itemId, itemsSection.getConfigurationSection(itemId));
                items.put(itemId, item);
                loaded++;
            } catch (Exception e) {
                failed++;
                logger.warning("Failed to load custom item '" + itemId + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        logger.info("Custom items loaded from resources: " + loaded + (failed > 0 ? " (" + failed + " failed)" : ""));
        return items;
    }

    private FileConfiguration loadConfigurationFromResource() {
        InputStream in = plugin.getResource(CONFIG_FILE);
        if (in == null) return null;

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            logger.severe("Error reading " + CONFIG_FILE + " from resources: " + e.getMessage());
            return null;
        }
    }

    private CustomItem parseItem(String itemId, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Item section is null");
        }

        String displayName = section.getString("display-name");
        if (displayName == null) {
            throw new IllegalArgumentException("Missing 'display-name' for item: " + itemId);
        }

        String materialStr = section.getString("material");
        if (materialStr == null) {
            throw new IllegalArgumentException("Missing 'material' for item: " + itemId);
        }

        Material material;
        try {
            material = Material.valueOf(materialStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid material '" + materialStr + "' for item: " + itemId);
        }

        List<String> lore = section.getStringList("lore");
        boolean glow = section.getBoolean("glow", false);

        String customModelData = section.getString("custom-model-data");

        return new CustomItem(itemId, displayName, material, lore, glow, customModelData);
    }
}