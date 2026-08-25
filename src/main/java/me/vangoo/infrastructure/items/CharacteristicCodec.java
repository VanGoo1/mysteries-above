package me.vangoo.infrastructure.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * Будує предмет «Характеристика» та читає його назад. Шлях+послідовність зберігаються у NBT через
 * {@link NBTBuilder} (та сама техніка, що для кастомних предметів).
 */
public final class CharacteristicCodec {

    public static final String NBT_PATHWAY = "characteristic_pathway";
    public static final String NBT_SEQUENCE = "characteristic_sequence";

    /**
     * Ключ custom-model-data для ресурс-паку. УНІВЕРСАЛЬНИЙ ({@code characteristic}) —
     * одна тонована модель на всі шляхи; колір задає компонент {@code DYED_COLOR}
     * (див. нижче), а не пер-шляховий ключ моделі. Мусить збігатися з єдиним кейсом
     * {@code "when": "characteristic"} у {@code items/music_disc_chirp.json}, інакше
     * select падає на ванільну модель без tint і колір не застосовується.
     */
    public static final String MODEL_KEY = "characteristic";

    public static String modelKeyFor(String pathwayName) {
        return MODEL_KEY;
    }

    /** Будує стак Характеристики для (шлях, seq). */
    public ItemStack create(String pathwayName, int sequence, int amount) {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_CHIRP, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PathwayBranding.textOf(pathwayName) + "Характеристика: " + pathwayName
                + ChatColor.GRAY + " [Seq " + sequence + "]");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Кристалічна есенція сили.",
                ChatColor.DARK_GRAY + "Замінює всі основні інгредієнти рецепта."
        ));
        // Диск у ванілі стакається до 1 — піднімаємо ліміт стака через компонент max_stack_size.
        DiscItems.applyStackSize(meta);
        // Незнищенний: захист від поломки/зношення (диск і так не має міцності — це підстраховка).
        meta.setUnbreakable(true);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DYE
        );

        // Підготовка під текстуру ресурс-паку (рядковий custom-model-data, як у CustomItemFactory).
        try {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(modelKeyFor(pathwayName)));
            meta.setCustomModelDataComponent(cmd);
        } catch (Throwable ignored) {
            // Старіше API без CustomModelDataComponent — пропускаємо, предмет лишається валідним.
        }

        item.setItemMeta(meta);

        ItemStack built = new NBTBuilder(item)
                .setString(NBT_PATHWAY, pathwayName)
                .setInt(NBT_SEQUENCE, sequence)
                .build();
        DiscItems.stripJukeboxPlayable(built);

        try {
            built.setData(
                    DataComponentTypes.DYED_COLOR,
                    DyedItemColor.dyedItemColor(PathwayBranding.liquidOf(pathwayName))
            );
        } catch (Throwable ignored) {}

        return built;
    }

    /** Чи є предмет Характеристикою (має NBT-мітку шляху). */
    public boolean isCharacteristic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        // Статична перевірка без клонування стака (NBTBuilder.hasKey сам обробляє null-meta).
        return NBTBuilder.hasKey(item, NBT_PATHWAY, PersistentDataType.STRING);
    }

    /** Читає (шлях, seq) з предмета, якщо це Характеристика. */
    public Optional<Characteristic> read(ItemStack item) {
        if (!isCharacteristic(item)) {
            return Optional.empty();
        }
        NBTBuilder builder = new NBTBuilder(item);
        Optional<String> pathway = builder.getString(item, NBT_PATHWAY);
        Optional<Integer> sequence = builder.getInt(item, NBT_SEQUENCE);
        if (pathway.isEmpty() || sequence.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Characteristic(pathway.get(), sequence.get()));
    }
}
