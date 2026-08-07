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
            "active", "passive", "permanent_passive", "characteristic", "gold_pound", "coppet",
            "characteristic_Error", "characteristic_Visionary", "characteristic_Door",
            "characteristic_Justiciar", "characteristic_WhiteTower", "characteristic_Fool",
            "characteristic_Sun", "characteristic_Tyrant", "characteristic_HangedMan",
            "characteristic_Hermit", "characteristic_Paragon", "characteristic_BlackEmperor",
            "characteristic_Darkness", "characteristic_Death", "characteristic_TwilightGiant",
            "characteristic_Mother", "characteristic_Moon", "characteristic_RedPriest",
            "characteristic_Demoness", "characteristic_Abyss", "characteristic_Chained",
            "characteristic_WheelOfFortune");

    @Test
    void valuesAreFrozenForever() {
        assertEquals(9049350, ItemModelData.of("active"));
        assertEquals(9960359, ItemModelData.of("passive"));
        assertEquals(2049622, ItemModelData.of("permanent_passive"));
        assertEquals(7313883, ItemModelData.of("characteristic"));
        assertEquals(9632173, ItemModelData.of("gold_pound"));
        assertEquals(5276955, ItemModelData.of("coppet"));

        // CharacteristicCodec.modelKeyFor — один ключ на pathway (PathwayManager.initializePathways).
        assertEquals(6170052, ItemModelData.of("characteristic_Error"));
        assertEquals(3772540, ItemModelData.of("characteristic_Visionary"));
        assertEquals(5796658, ItemModelData.of("characteristic_Door"));
        assertEquals(2336144, ItemModelData.of("characteristic_Justiciar"));
        assertEquals(1889188, ItemModelData.of("characteristic_WhiteTower"));
        assertEquals(5856234, ItemModelData.of("characteristic_Fool"));
        assertEquals(3418952, ItemModelData.of("characteristic_Sun"));
        assertEquals(1487102, ItemModelData.of("characteristic_Tyrant"));
        assertEquals(1355461, ItemModelData.of("characteristic_HangedMan"));
        assertEquals(8478247, ItemModelData.of("characteristic_Hermit"));
        assertEquals(8142562, ItemModelData.of("characteristic_Paragon"));
        assertEquals(6148061, ItemModelData.of("characteristic_BlackEmperor"));
        assertEquals(9424145, ItemModelData.of("characteristic_Darkness"));
        assertEquals(4843056, ItemModelData.of("characteristic_Death"));
        assertEquals(5713489, ItemModelData.of("characteristic_TwilightGiant"));
        assertEquals(7913863, ItemModelData.of("characteristic_Mother"));
        assertEquals(6064773, ItemModelData.of("characteristic_Moon"));
        assertEquals(5559724, ItemModelData.of("characteristic_RedPriest"));
        assertEquals(7019902, ItemModelData.of("characteristic_Demoness"));
        assertEquals(2006164, ItemModelData.of("characteristic_Abyss"));
        assertEquals(8478236, ItemModelData.of("characteristic_Chained"));
        assertEquals(8933587, ItemModelData.of("characteristic_WheelOfFortune"));
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
