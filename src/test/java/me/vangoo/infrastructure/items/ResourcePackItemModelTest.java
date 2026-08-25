package me.vangoo.infrastructure.items;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Предмети плагіну стоять на музичних пластинках, а їхній вигляд задає ресурс-пак через
 * {@code custom_model_data}: {@code assets/minecraft/items/<material>.json} мапить ключ моделі на
 * файл у {@code models/item/}. Обидва боки зв'язані ЛИШЕ рядком — одрук чи забутий файл виявляється
 * аж на клієнті, де предмет тихо стає звичайною пластинкою.
 *
 * <p>Тест пінить два боки цього зв'язку:
 * <ol>
 *   <li>кожен {@code custom-model-data} з {@code custom-items.yml}, чий матеріал має визначення в
 *       паку, покритий кейсом у цьому визначенні;</li>
 *   <li>кожна модель, на яку посилається визначення, реально існує в {@code models/item/}.</li>
 * </ol>
 *
 * <p>Предмет, чий матеріал взагалі не має файлу в {@code items/} (сьогодні —
 * {@code MUSIC_DISC_11} орденів і {@code ENCHANTED_BOOK} книги рецептів), свідомо пропускається:
 * він законно падає на ванільний вигляд, доки для нього не намалюють текстуру.
 */
class ResourcePackItemModelTest {

    private static final File CONFIG = new File("src/main/resources/custom-items.yml");
    private static final File PACK_ITEMS = new File("mysteries-resourcepack/assets/minecraft/items");
    private static final File PACK_MODELS = new File("mysteries-resourcepack/assets/minecraft/models/item");

    private static final Pattern CASE_KEY = Pattern.compile("\"when\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MODEL_REF = Pattern.compile("\"model\"\\s*:\\s*\"item/([^\"]+)\"");

    @Test
    void everyConfiguredModelKeyIsCoveredByItsMaterialDefinition() {
        ConfigurationSection items = customItems();

        List<String> missing = new ArrayList<>();
        for (String id : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(id);
            if (item == null) {
                continue;
            }
            String modelKey = item.getString("custom-model-data");
            String material = item.getString("material");
            if (modelKey == null || material == null) {
                continue;
            }
            File definition = new File(PACK_ITEMS, material.toLowerCase(Locale.ROOT) + ".json");
            if (!definition.isFile()) {
                // Матеріал без визначення в паку — законний ванільний фолбек, не помилка.
                continue;
            }
            if (!casesOf(definition).contains(modelKey)) {
                missing.add(id + " (" + material + " → \"" + modelKey + "\")");
            }
        }

        assertTrue(missing.isEmpty(),
                "custom-model-data без кейсу в items/<material>.json — предмет стане звичайною пластинкою: "
                        + missing);
    }

    @Test
    void everyModelReferencedByThePackExists() {
        List<File> definitions = definitionFiles();
        assertFalse(definitions.isEmpty(), "У паку немає жодного items/*.json — шлях зламано");

        List<String> missing = new ArrayList<>();
        for (File definition : definitions) {
            Matcher matcher = MODEL_REF.matcher(read(definition));
            while (matcher.find()) {
                String model = matcher.group(1);
                if (isVanillaFallback(definition, model)) {
                    continue;
                }
                if (!new File(PACK_MODELS, model + ".json").isFile()) {
                    missing.add(definition.getName() + " → models/item/" + model + ".json");
                }
            }
        }

        assertTrue(missing.isEmpty(), "Визначення посилається на неіснуючу модель: " + missing);
    }

    /** Фолбек кожного визначення вказує на ванільну модель самого матеріалу — свого файлу не має. */
    private boolean isVanillaFallback(File definition, String model) {
        return model.equals(definition.getName().replace(".json", ""));
    }

    private Set<String> casesOf(File definition) {
        Set<String> keys = new HashSet<>();
        Matcher matcher = CASE_KEY.matcher(read(definition));
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private List<File> definitionFiles() {
        File[] files = PACK_ITEMS.listFiles((dir, name) -> name.endsWith(".json"));
        return files == null ? List.of() : List.of(files);
    }

    private ConfigurationSection customItems() {
        assertTrue(CONFIG.isFile(), "custom-items.yml не знайдено: " + CONFIG.getAbsolutePath());
        ConfigurationSection section =
                YamlConfiguration.loadConfiguration(CONFIG).getConfigurationSection("custom-items");
        assertTrue(section != null && !section.getKeys(false).isEmpty(),
                "custom-items.yml порожній — шлях або схема зламані");
        return section;
    }

    private String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося прочитати " + file, e);
        }
    }
}
