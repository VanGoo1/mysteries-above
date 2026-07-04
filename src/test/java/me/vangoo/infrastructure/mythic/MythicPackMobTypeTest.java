package me.vangoo.infrastructure.mythic;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MythicMobs loads every entry in Mobs/*.yml as a mob — templates included.
 * Template merging happens on raw configs, but a mob whose merged config has
 * no Type fails to load ("Could not load MythicMob X! No Type specified.")
 * and spams a config error on every boot. Every entry must therefore resolve
 * a Type either directly or through its Template chain.
 */
class MythicPackMobTypeTest {

    private static final File MOBS_DIR = new File("src/main/resources/mythic-pack/Mobs");

    @Test
    void everyMobEntryResolvesTypeThroughTemplateChain() {
        Map<String, ConfigurationSection> mobs = loadAllMobEntries();
        assertFalse(mobs.isEmpty(), "No mob entries found in " + MOBS_DIR + " — path is broken");

        List<String> untyped = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : mobs.entrySet()) {
            if (!resolvesType(entry.getKey(), mobs, 0)) {
                untyped.add(entry.getKey());
            }
        }
        assertTrue(untyped.isEmpty(),
                "Mobs without a resolvable Type (MythicMobs will fail to load them): " + untyped);
    }

    private boolean resolvesType(String mobId, Map<String, ConfigurationSection> mobs, int depth) {
        if (depth > 10) return false; // template cycle guard
        ConfigurationSection mob = mobs.get(mobId);
        if (mob == null) return false;
        if (mob.getString("Type") != null) return true;
        String template = mob.getString("Template");
        return template != null && resolvesType(template, mobs, depth + 1);
    }

    private Map<String, ConfigurationSection> loadAllMobEntries() {
        Map<String, ConfigurationSection> mobs = new HashMap<>();
        File[] files = MOBS_DIR.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return mobs;
        for (File file : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                ConfigurationSection section = yml.getConfigurationSection(key);
                if (section != null) {
                    mobs.put(key, section);
                }
            }
        }
        return mobs;
    }
}
