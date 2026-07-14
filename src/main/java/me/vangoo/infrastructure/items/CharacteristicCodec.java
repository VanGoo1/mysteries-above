package me.vangoo.infrastructure.items;

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
     * Префікс ключа custom-model-data для ресурс-паку. Тепер ПЕР-ШЛЯХОВИЙ
     * ({@code characteristic_<pathway>}) — кожен шлях можна тонувати окремо.
     */
    public static final String MODEL_KEY_PREFIX = "characteristic_";

    public static String modelKeyFor(String pathwayName) {
        return (MODEL_KEY_PREFIX + pathwayName).toLowerCase(java.util.Locale.ROOT);
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
        meta.setMaxStackSize(64);
        // Незнищенний: захист від поломки/зношення (диск і так не має міцності — це підстраховка).
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true); // лише для світіння
        meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_UNBREAKABLE
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
        tryStripJukeboxPlayable(built);
        return built;
    }

    /**
     * Best-effort: прибирає компонент {@code jukebox_playable}, щоб Характеристику не можна було
     * вставити в jukebox. Реалізовано через Paper DataComponent API ({@code ItemStack.unsetData(
     * DataComponentTypes.JUKEBOX_PLAYABLE)}) РЕФЛЕКСІЄЮ — бо компілюємось проти spigot-api, який не
     * має цього API. На сервері Paper (чи форках із цим API) спрацьовує; на чистому Spigot мовчки
     * нічого не робить (там для блокування потрібен лістенер на PlayerInteractEvent з jukebox).
     */
    private void tryStripJukeboxPlayable(ItemStack item) {
        try {
            Class<?> typesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Class<?> typeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
            Object jukeboxType = typesClass.getField("JUKEBOX_PLAYABLE").get(null);
            item.getClass().getMethod("unsetData", typeClass).invoke(item, jukeboxType);
        } catch (Throwable ignored) {
            // Spigot/стара версія без DataComponent API — лишаємо предмет як є.
        }
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
