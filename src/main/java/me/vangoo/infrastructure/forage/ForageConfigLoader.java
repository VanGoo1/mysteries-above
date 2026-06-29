package me.vangoo.infrastructure.forage;

import me.vangoo.domain.forage.ForageEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Завантажує forage.yml у ForageConfig. Дзеркалить CreatureConfigLoader. */
public class ForageConfigLoader {

    private final Plugin plugin;

    public ForageConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public ForageConfig load() {
        File file = new File(plugin.getDataFolder(), "forage.yml");
        if (!file.exists()) {
            plugin.saveResource("forage.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("forage");

        long interval = 40L;
        double chance = 0.5;
        int maxNearby = 5;
        long ttl = 120L;
        int radius = 12;
        Set<Material> vegetation = new HashSet<>();
        Map<String, List<ForageEntry>> biomes = new LinkedHashMap<>();

        if (root == null) {
            plugin.getLogger().warning("No 'forage' section in forage.yml. Foraging disabled (empty config).");
            return new ForageConfig(interval, chance, maxNearby, ttl, radius, vegetation, biomes);
        }

        ConfigurationSection spawn = root.getConfigurationSection("spawn");
        if (spawn != null) {
            interval = spawn.getLong("interval-seconds", interval);
            chance = spawn.getDouble("chance", chance);
            maxNearby = spawn.getInt("max-nearby", maxNearby);
            ttl = spawn.getLong("ttl-seconds", ttl);
            radius = spawn.getInt("search-radius", radius);
            for (String name : spawn.getStringList("vegetation")) {
                Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (m == null) {
                    plugin.getLogger().warning("forage.yml: unknown vegetation material '" + name + "', skipping");
                } else {
                    vegetation.add(m);
                }
            }
        }

        ConfigurationSection biomesSection = root.getConfigurationSection("biomes");
        if (biomesSection != null) {
            for (String biome : biomesSection.getKeys(false)) {
                List<ForageEntry> entries = new ArrayList<>();
                for (Map<?, ?> raw : biomesSection.getMapList(biome)) {
                    Object idObj = raw.get("id");
                    if (idObj == null) {
                        plugin.getLogger().warning("forage.yml: entry missing 'id' in biome '" + biome + "', skipping");
                        continue;
                    }
                    int weight = toInt(raw.get("weight"), 1);
                    if (weight <= 0) weight = 1;
                    entries.add(new ForageEntry(String.valueOf(idObj), weight));
                }
                if (!entries.isEmpty()) {
                    biomes.put(biome.toUpperCase(Locale.ROOT), entries);
                }
            }
        }

        plugin.getLogger().info("Loaded forage config: " + biomes.size() + " biomes, "
                + vegetation.size() + " vegetation materials");
        return new ForageConfig(interval, chance, maxNearby, ttl, radius, vegetation, biomes);
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
