package me.vangoo.infrastructure.mythic;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Копіює вшитий MythicMobs-пак у plugins/MythicMobs/Packs/MysteriesAbove.
 * Репозиторій — єдине джерело правди: файли на сервері перезаписуються при розбіжності. */
public final class MythicPackInstaller {

    private static final String[] PACK_FILES = {
            "pack.yml",
            "Mobs/templates.yml",
            "Mobs/error.yml", "Mobs/visionary.yml", "Mobs/door.yml",
            "Mobs/justiciar.yml", "Mobs/whitetower.yml", "Mobs/fool.yml",
            "Mobs/sun.yml",
            "Skills/error.yml", "Skills/visionary.yml", "Skills/door.yml",
            "Skills/justiciar.yml", "Skills/whitetower.yml", "Skills/fool.yml",
    };

    private final Plugin plugin;

    public MythicPackInstaller(Plugin plugin) {
        this.plugin = plugin;
    }

    /** @return true, якщо хоч один файл було створено/оновлено (потрібен mm reload). */
    public boolean installOrUpdate() {
        Path packRoot = plugin.getDataFolder().toPath()
                .resolveSibling("MythicMobs").resolve("Packs").resolve("MysteriesAbove");
        int updated = 0;
        for (String rel : PACK_FILES) {
            try (InputStream in = plugin.getResource("mythic-pack/" + rel)) {
                if (in == null) {
                    plugin.getLogger().warning("Mythic pack resource missing in jar: " + rel);
                    continue;
                }
                byte[] data = in.readAllBytes();
                Path target = packRoot.resolve(rel);
                if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), data)) {
                    Files.createDirectories(target.getParent());
                    Files.write(target, data);
                    updated++;
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to install mythic pack file " + rel + ": " + e);
            }
        }
        if (updated > 0) {
            plugin.getLogger().info("Mythic pack: installed/updated " + updated + " file(s)");
        }
        return updated > 0;
    }
}
