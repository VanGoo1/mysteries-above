package me.vangoo.infrastructure.items;

import me.vangoo.domain.brewing.RecipeDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Завантажує рецепти зілля з {@code potion-recipes.yml} (тека плагіна, із fallback на ресурс JAR).
 * Повертає мапу: назва шляху → (послідовність → {@link RecipeDefinition}).
 */
public class PotionRecipeConfigLoader {

    private static final String CONFIG_FILE = "potion-recipes.yml";
    private static final String ROOT_KEY = "recipes";

    private final Plugin plugin;

    public PotionRecipeConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Map<Integer, RecipeDefinition>> load() {
        Map<String, Map<Integer, RecipeDefinition>> result = new HashMap<>();

        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection recipes = config.getConfigurationSection(ROOT_KEY);
        if (recipes == null) {
            plugin.getLogger().warning("No '" + ROOT_KEY + "' section found in " + CONFIG_FILE);
            return result;
        }

        int loaded = 0;
        for (String pathway : recipes.getKeys(false)) {
            ConfigurationSection sequences = recipes.getConfigurationSection(pathway);
            if (sequences == null) {
                continue;
            }
            Map<Integer, RecipeDefinition> perSequence = new HashMap<>();
            for (String seqKey : sequences.getKeys(false)) {
                int sequence;
                try {
                    sequence = Integer.parseInt(seqKey);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid sequence '" + seqKey + "' for pathway " + pathway
                            + " in " + CONFIG_FILE);
                    continue;
                }
                ConfigurationSection recipeSection = sequences.getConfigurationSection(seqKey);
                if (recipeSection == null) {
                    continue;
                }
                List<String> main = recipeSection.getStringList("main");
                List<String> aux = recipeSection.getStringList("auxiliary");
                if (main.isEmpty() && aux.isEmpty()) {
                    plugin.getLogger().warning("Recipe " + pathway + " Seq " + sequence
                            + " in " + CONFIG_FILE + " has no 'main' or 'auxiliary' ingredients — skipped");
                    continue;
                }
                perSequence.put(sequence, new RecipeDefinition(main, aux));
                loaded++;
            }
            if (!perSequence.isEmpty()) {
                result.put(pathway, perSequence);
            }
        }

        plugin.getLogger().info("Loaded potion recipes for " + result.size() + " pathways (" + loaded + " recipes)");
        return result;
    }
}
