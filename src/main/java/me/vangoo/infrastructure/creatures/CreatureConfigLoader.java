package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureStats;
import me.vangoo.domain.creatures.CreatureTier;
import me.vangoo.domain.creatures.SpawnRule;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/** Завантажує creatures.yml у Map&lt;id, CreatureDefinition&gt;. Дзеркалить LootTableConfigLoader. */
public class CreatureConfigLoader {

    private final Plugin plugin;

    public CreatureConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, CreatureDefinition> load() {
        File file = new File(plugin.getDataFolder(), "creatures.yml");
        if (!file.exists()) {
            plugin.saveResource("creatures.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, CreatureDefinition> result = new LinkedHashMap<>();

        ConfigurationSection root = config.getConfigurationSection("creatures");
        if (root == null) {
            plugin.getLogger().warning("No 'creatures' section in creatures.yml. No creatures loaded.");
            return result;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            try {
                result.put(id, parseCreature(id, c));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping creature '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + result.size() + " creatures from creatures.yml");
        return result;
    }

    private CreatureDefinition parseCreature(String id, ConfigurationSection c) {
        String baseEntity = c.getString("base_entity");
        if (baseEntity == null || baseEntity.isEmpty()) {
            throw new IllegalArgumentException("missing base_entity");
        }
        String displayName = c.getString("display_name", id);
        CreatureTier tier = CreatureTier.valueOf(c.getString("tier", "common").toUpperCase(Locale.ROOT));
        boolean clearDrops = c.getBoolean("clear_vanilla_drops", true);
        String appearance = c.getString("appearance", "vanilla");

        CreatureStats stats = parseStats(c.getConfigurationSection("stats"));
        Map<String, String> equipment = parseEquipment(c.getConfigurationSection("equipment"));
        LootTableData loot = parseLoot(c.getConfigurationSection("loot"));
        SpawnRule spawn = parseSpawn(c.getConfigurationSection("spawn"));

        String pathway = c.getString("pathway");
        if (pathway == null || pathway.isEmpty()) {
            int underscore = id.indexOf('_');
            pathway = (underscore > 0 ? id.substring(0, underscore) : id);
        }
        pathway = pathway.toLowerCase(Locale.ROOT);

        return new CreatureDefinition(id, baseEntity.toUpperCase(Locale.ROOT), displayName, tier,
                stats, equipment, appearance, loot, spawn, clearDrops, pathway);
    }

    private CreatureStats parseStats(ConfigurationSection s) {
        if (s == null) return new CreatureStats(20, 4, 0.25, 1.0);
        return new CreatureStats(
                s.getDouble("health", 20),
                s.getDouble("damage", 4),
                s.getDouble("speed", 0.25),
                s.getDouble("scale", 1.0));
    }

    private Map<String, String> parseEquipment(ConfigurationSection s) {
        Map<String, String> map = new LinkedHashMap<>();
        if (s == null) return map;
        for (String slot : s.getKeys(false)) {
            map.put(slot.toUpperCase(Locale.ROOT), s.getString(slot));
        }
        return map;
    }

    private LootTableData parseLoot(ConfigurationSection s) {
        if (s == null) return new LootTableData(new ArrayList<>(), 1, 1);
        int min = s.getInt("min_items", 1);
        int max = s.getInt("max_items", 1);
        List<LootItem> items = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("items")) {
            Object idObj = raw.get("id");
            if (idObj == null) {
                plugin.getLogger().warning("Loot item missing 'id' in a creature; skipping entry");
                continue;
            }
            String itemId = String.valueOf(idObj);
            int weight = toInt(raw.get("weight"), 1);
            int amin = toInt(raw.get("min"), 1);
            int amax = toInt(raw.get("max"), amin);
            if (weight <= 0) weight = 1;
            if (amin < 1) amin = 1;
            if (amax < amin) amax = amin;
            items.add(new LootItem(itemId, weight, amin, amax));
        }
        return new LootTableData(items, min, max);
    }

    private SpawnRule parseSpawn(ConfigurationSection s) {
        if (s == null) {
            return new SpawnRule(List.of(), List.of(), 0.0, List.of(), 0.0);
        }
        ConfigurationSection nat = s.getConfigurationSection("natural");
        ConfigurationSection str = s.getConfigurationSection("structure");
        List<String> biomes = nat == null ? List.of() : upper(nat.getStringList("biomes"));
        List<String> replace = nat == null ? List.of() : upper(nat.getStringList("replace"));
        double natChance = nat == null ? 0.0 : nat.getDouble("chance", 0.0);
        List<String> keys = str == null ? List.of() : str.getStringList("keys");
        double strChance = str == null ? 0.0 : str.getDouble("chance", 0.0);
        return new SpawnRule(biomes, replace, natChance, keys, strChance);
    }

    private List<String> upper(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String v : in) out.add(v.toUpperCase(Locale.ROOT));
        return out;
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
