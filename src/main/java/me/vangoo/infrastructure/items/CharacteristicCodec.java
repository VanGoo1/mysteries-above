package me.vangoo.infrastructure.items;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    /** Будує стак Характеристики для (шлях, seq). */
    public ItemStack create(String pathwayName, int sequence, int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Характеристика: " + pathwayName
                + ChatColor.GRAY + " [Seq " + sequence + "]");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Кристалічна есенція сили.",
                ChatColor.DARK_GRAY + "Замінює всі основні інгредієнти рецепта."
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);

        return new NBTBuilder(item)
                .setString(NBT_PATHWAY, pathwayName)
                .setInt(NBT_SEQUENCE, sequence)
                .build();
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
