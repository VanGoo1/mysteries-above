package me.vangoo.infrastructure.structures;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Інгредієнт існує в грі рівно тоді, коли його можна ДОБУТИ. `custom-items.yml` описує предмет,
 * `potion-recipes.yml` каже, до якого шляху й Послідовності він належить, а `global_loot.yml`
 * вирішує, чи трапиться він у скрині. Розхід між ними нічого не ламає при старті — просто зілля
 * стає неварабельним, і виявляється це через тижні гри (так і сталось: п'ять шляхів — Error,
 * Fool, Sun, Tyrant, Death — 94 інгредієнти без жодного запису в луті).
 *
 * <p>Тест пінить обидва боки зв'язку. Валюта, шифровки орденів і службові предмети
 * (`master_recipe_book`) під нього не підпадають: перші не є інгредієнтами, другий не мусить
 * лежати в скринях.
 */
class GlobalLootCoverageTest {

    private static final File LOOT = new File("src/main/resources/global_loot.yml");
    private static final File RECIPES = new File("src/main/resources/potion-recipes.yml");
    private static final File ITEMS = new File("src/main/resources/custom-items.yml");

    @Test
    void everyBrewingIngredientIsObtainableFromLoot() {
        Set<String> loot = lootItemIds();
        Set<String> missing = new TreeSet<>();

        ConfigurationSection recipes = section(RECIPES, "recipes");
        for (String pathway : recipes.getKeys(false)) {
            ConfigurationSection perSequence = recipes.getConfigurationSection(pathway);
            if (perSequence == null) {
                continue;
            }
            for (String sequence : perSequence.getKeys(false)) {
                List<String> ingredients = new ArrayList<>();
                ingredients.addAll(perSequence.getStringList(sequence + ".main"));
                ingredients.addAll(perSequence.getStringList(sequence + ".auxiliary"));
                for (String ingredient : ingredients) {
                    if (!loot.contains(ingredient)) {
                        missing.add(pathway + ":" + sequence + " → " + ingredient);
                    }
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "Інгредієнт із potion-recipes.yml не трапляється в скринях — зілля неварабельне. "
                        + "Додай запис у global_loot.yml (ваги за Послідовністю — у шапці файлу): "
                        + missing);
    }

    @Test
    void everyLootItemIsADefinedCustomItem() {
        Set<String> defined = section(ITEMS, "custom-items").getKeys(false);
        Set<String> unknown = new TreeSet<>();

        for (String itemId : lootItemIds()) {
            // Префіксовані id (potion:/recipe:/currency:) збирає LootGenerationService на льоту.
            if (itemId.contains(":")) {
                continue;
            }
            if (!defined.contains(itemId)) {
                unknown.add(itemId);
            }
        }

        assertTrue(unknown.isEmpty(),
                "global_loot.yml посилається на предмет, якого немає в custom-items.yml — "
                        + "лут тихо пропустить його: " + unknown);
    }

    private Set<String> lootItemIds() {
        ConfigurationSection items = section(LOOT, "global_loot.items");
        Set<String> ids = new HashSet<>();
        for (String key : items.getKeys(false)) {
            String itemId = items.getString(key + ".item_id");
            if (itemId != null) {
                ids.add(itemId);
            }
        }
        return ids;
    }

    private ConfigurationSection section(File file, String path) {
        assertTrue(file.isFile(), file + " не знайдено: " + file.getAbsolutePath());
        ConfigurationSection section =
                YamlConfiguration.loadConfiguration(file).getConfigurationSection(path);
        assertTrue(section != null && !section.getKeys(false).isEmpty(),
                file.getName() + ": секція " + path + " порожня — схема зламана");
        return section;
    }
}
