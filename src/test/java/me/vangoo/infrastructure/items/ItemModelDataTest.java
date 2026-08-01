package me.vangoo.infrastructure.items;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ItemModelData} згортає читабельний ключ моделі в число {@code custom_model_data}
 * (на 1.21.1 рядкових ключів ще немає). Дві властивості цієї функції критичні:
 *
 * <ul>
 *   <li><b>стабільність</b> — у гравців в інвентарях лежать уже проштамповані предмети, тож
 *       значення для ключа не сміє змінитись НІКОЛИ; тому воно пришпилене числами тут;</li>
 *   <li><b>унікальність</b> — два ключі одного матеріалу з однаковим числом дали б предмету
 *       чужу текстуру, і виявилось би це аж на клієнті.</li>
 * </ul>
 */
class ItemModelDataTest {

    private static final File CONFIG = new File("src/main/resources/custom-items.yml");

    /** Сталі ключі, які ставить код, а не конфіг (AbilityItemFactory / CharacteristicCodec / CurrencyCodec). */
    private static final List<String> CODE_KEYS = List.of(
            "active", "passive", "permanent_passive", "characteristic", "gold_pound", "coppet");

    @Test
    void valuesAreFrozenForever() {
        assertEquals(9049350, ItemModelData.of("active"));
        assertEquals(9960359, ItemModelData.of("passive"));
        assertEquals(2049622, ItemModelData.of("permanent_passive"));
        assertEquals(7313883, ItemModelData.of("characteristic"));
        assertEquals(9632173, ItemModelData.of("gold_pound"));
        assertEquals(5276955, ItemModelData.of("coppet"));
    }

    @Test
    void valuesStayInTheReservedRange() {
        for (String key : allKeys()) {
            int data = ItemModelData.of(key);
            assertTrue(data >= 1_000_000 && data <= 9_999_999,
                    key + " дав " + data + " — поза зарезервованим діапазоном");
        }
    }

    @Test
    void noTwoKeysShareTheSameValue() {
        Map<Integer, String> seen = new HashMap<>();
        for (String key : allKeys()) {
            String clash = seen.put(ItemModelData.of(key), key);
            assertTrue(clash == null || clash.equals(key),
                    "колізія custom_model_data: '" + key + "' і '" + clash + "'");
        }
    }

    @Test
    void blankKeyIsIgnoredInsteadOfThrowing() {
        ItemModelData.apply(null, "active");   // no-op, без NPE
    }

    private List<String> allKeys() {
        List<String> keys = new ArrayList<>(CODE_KEYS);
        ConfigurationSection items =
                YamlConfiguration.loadConfiguration(CONFIG).getConfigurationSection("custom-items");
        assertTrue(items != null && !items.getKeys(false).isEmpty(),
                "custom-items.yml порожній — шлях або схема зламані");
        for (String id : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(id);
            String key = item == null ? null : item.getString("custom-model-data");
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }
}
