package me.vangoo.infrastructure.mythic;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kitcast{skills=...}, randomskill{skills=...}, skill{s=...} та onHitSkill=... посилаються
 * на метаскіли за іменем-рядком; одрук виявляється лише в рантаймі ("Skill not found" у
 * консолі при касті). Тест пінить: кожне посилання з пака існує у Skills/*.yml.
 * Обмеження: скан регекспами припускає, що s= стоїть одразу після дужки (skill{s=...});
 * варіант з іншим порядком атрибутів (skill{delay=5;s=X}) тест не побачить.
 */
class MythicPackKitReferenceTest {

    private static final File PACK_DIR = new File("src/main/resources/mythic-pack");
    private static final List<Pattern> LIST_REFERENCES = List.of(
            Pattern.compile("kitcast\\{[^}]*?skills=([^;}]+)"),
            Pattern.compile("randomskill\\{[^}]*?skills=([^;}]+)"));
    private static final List<Pattern> SINGLE_REFERENCES = List.of(
            Pattern.compile("skill\\{s=([A-Za-z0-9_]+)"),
            Pattern.compile("onHitSkill=([A-Za-z0-9_]+)"));

    @Test
    void everyReferencedMetaskillExists() throws IOException {
        Set<String> defined = definedMetaskills();
        assertFalse(defined.isEmpty(), "No metaskills found in Skills/*.yml — path is broken");

        List<String> missing = new ArrayList<>();
        for (File yml : packYmlFiles()) {
            String content = Files.readString(yml.toPath());
            for (String ref : referencedSkills(content)) {
                if (!defined.contains(ref)) {
                    missing.add(yml.getName() + " -> " + ref);
                }
            }
        }
        assertTrue(missing.isEmpty(), "References to missing metaskills: " + missing);
    }

    private Set<String> referencedSkills(String content) {
        Set<String> refs = new HashSet<>();
        for (Pattern pattern : SINGLE_REFERENCES) {
            Matcher m = pattern.matcher(content);
            while (m.find()) refs.add(m.group(1));
        }
        for (Pattern pattern : LIST_REFERENCES) {
            Matcher m = pattern.matcher(content);
            while (m.find()) {
                for (String entry : m.group(1).split(",")) {
                    refs.add(entry.trim().split(":")[0]);
                }
            }
        }
        return refs;
    }

    private Set<String> definedMetaskills() {
        Set<String> defined = new HashSet<>();
        File[] files = new File(PACK_DIR, "Skills").listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(files, "Skills dir missing");
        for (File file : files) {
            defined.addAll(YamlConfiguration.loadConfiguration(file).getKeys(false));
        }
        return defined;
    }

    private List<File> packYmlFiles() {
        List<File> all = new ArrayList<>();
        for (String sub : List.of("Mobs", "Skills")) {
            File[] files = new File(PACK_DIR, sub).listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) all.addAll(Arrays.asList(files));
        }
        return all;
    }
}
