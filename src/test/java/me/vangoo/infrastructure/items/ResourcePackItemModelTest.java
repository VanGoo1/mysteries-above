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
 * {@code custom_model_data}: {@code assets/minecraft/models/item/<material>.json} мапить ЧИСЛО
 * (яке рахує {@link ItemModelData} з читабельного ключа) на файл моделі в тій самій теці.
 * Обидва боки зв'язані ЛИШЕ цим числом — одрук чи забутий файл виявляється аж на клієнті, де
 * предмет тихо стає звичайною пластинкою.
 *
 * <p>Тест пінить три боки цього зв'язку:
 * <ol>
 *   <li>кожен {@code custom-model-data} з {@code custom-items.yml}, чий матеріал має модель у
 *       паку, покритий override'ом із ПРАВИЛЬНИМ числом;</li>
 *   <li>кожна модель, на яку посилається override, реально існує;</li>
 *   <li>override'и йдуть за ЗРОСТАННЯМ числа — ванільний числовий предикат матчить {@code >=} і
 *       виграє останній збіг, тож зворотний порядок мовчки підставив би чужу модель.</li>
 * </ol>
 *
 * <p>Предмет, чий матеріал взагалі не має моделі з override'ами (сьогодні —
 * {@code MUSIC_DISC_11} орденів і {@code ENCHANTED_BOOK} книги рецептів), свідомо пропускається:
 * він законно падає на ванільний вигляд, доки для нього не намалюють текстуру.
 */
class ResourcePackItemModelTest {

    private static final File CONFIG = new File("src/main/resources/custom-items.yml");
    private static final File PACK_MODELS = new File("mysteries-resourcepack/assets/minecraft/models/item");

    private static final Pattern OVERRIDE = Pattern.compile(
            "\"custom_model_data\"\\s*:\\s*(\\d+)\\s*\\}\\s*,\\s*\"model\"\\s*:\\s*\"item/([^\"]+)\"");

    @Test
    void everyConfiguredModelKeyIsCoveredByItsMaterialModel() {
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
            File definition = new File(PACK_MODELS, material.toLowerCase(Locale.ROOT) + ".json");
            if (!definition.isFile() || !read(definition).contains("\"overrides\"")) {
                // Матеріал без override'ів у паку — законний ванільний фолбек, не помилка.
                continue;
            }
            if (!dataValuesOf(definition).contains(ItemModelData.of(modelKey))) {
                missing.add(id + " (" + material + " → \"" + modelKey + "\" = "
                        + ItemModelData.of(modelKey) + ")");
            }
        }

        assertTrue(missing.isEmpty(),
                "custom-model-data без override'а в models/item/<material>.json — предмет стане "
                        + "звичайною пластинкою (перегенеруй пак: tools/resourcepack/rp-item-models.gen.ps1): "
                        + missing);
    }

    @Test
    void everyModelReferencedByThePackExists() {
        List<File> definitions = definitionFiles();
        assertFalse(definitions.isEmpty(),
                "У паку немає жодної моделі пластинки з override'ами — шлях зламано");

        List<String> missing = new ArrayList<>();
        for (File definition : definitions) {
            Matcher matcher = OVERRIDE.matcher(read(definition));
            while (matcher.find()) {
                String model = matcher.group(2);
                if (!new File(PACK_MODELS, model + ".json").isFile()) {
                    missing.add(definition.getName() + " → models/item/" + model + ".json");
                }
            }
        }

        assertTrue(missing.isEmpty(), "Override посилається на неіснуючу модель: " + missing);
    }

    @Test
    void overridesAreSortedAscending() {
        for (File definition : definitionFiles()) {
            List<Integer> values = new ArrayList<>();
            Matcher matcher = OVERRIDE.matcher(read(definition));
            while (matcher.find()) {
                values.add(Integer.parseInt(matcher.group(1)));
            }
            for (int i = 1; i < values.size(); i++) {
                assertTrue(values.get(i - 1) < values.get(i),
                        definition.getName() + ": override'и мусять іти за зростанням "
                                + "custom_model_data (предикат матчить >= і виграє останній збіг), "
                                + "але " + values.get(i - 1) + " стоїть перед " + values.get(i));
            }
        }
    }

    private Set<Integer> dataValuesOf(File definition) {
        Set<Integer> values = new HashSet<>();
        Matcher matcher = OVERRIDE.matcher(read(definition));
        while (matcher.find()) {
            values.add(Integer.parseInt(matcher.group(1)));
        }
        return values;
    }

    /** Моделі пластинок, що реально несуть override'и (решта моделей — самі цілі override'ів). */
    private List<File> definitionFiles() {
        File[] files = PACK_MODELS.listFiles(
                (dir, name) -> name.startsWith("music_disc_") && name.endsWith(".json"));
        if (files == null) {
            return List.of();
        }
        List<File> withOverrides = new ArrayList<>();
        for (File file : files) {
            if (read(file).contains("\"overrides\"")) {
                withOverrides.add(file);
            }
        }
        return withOverrides;
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
