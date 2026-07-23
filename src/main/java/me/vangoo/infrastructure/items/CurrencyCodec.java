package me.vangoo.infrastructure.items;

import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * Монети підпільного ринку: «Золотий фунт» і «Коппет» (1 фунт = 20 коппетів).
 * Валюта НЕ прив'язана до зборів — інші механіки можуть видавати/приймати її.
 */
public final class CurrencyCodec {

    public static final String NBT_COIN = "currency_coin";
    public static final String COIN_POUND = "pound";
    public static final String COIN_COPPET = "coppet";
    public static final String MODEL_POUND = "gold_pound";
    public static final String MODEL_COPPET = "coppet";

    public ItemStack createPounds(int amount) {
        return createCoin(Material.MUSIC_DISC_MELLOHI, amount, COIN_POUND, MODEL_POUND,
                ChatColor.GOLD + "Золотий фунт",
                ChatColor.GRAY + "Тверда валюта Потойбічних.",
                ChatColor.DARK_GRAY + "1 фунт = 20 коппетів");
    }

    public ItemStack createCoppets(int amount) {
        return createCoin(Material.MUSIC_DISC_STAL, amount, COIN_COPPET, MODEL_COPPET,
                ChatColor.YELLOW + "Коппет",
                ChatColor.GRAY + "Дрібна монета Потойбічних.",
                ChatColor.DARK_GRAY + "20 коппетів = 1 фунт");
    }

    private ItemStack createCoin(Material material, int amount, String coinType, String modelKey,
                                 String displayName, String loreLine1, String loreLine2) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(List.of("", loreLine1, loreLine2));
        // Диск у ванілі стакається до 1 — піднімаємо ліміт стака (гаманець стакає монети до 64).
        DiscItems.applyStackSize(meta);
        // Незнищенний: диск не має міцності, це підстраховка від будь-якого зношення.
        meta.setUnbreakable(true);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE
        );
        // Підготовка під текстуру ресурс-паку (рядковий custom-model-data, як у Характеристик):
        // MODEL_POUND/MODEL_COPPET — стабільні ключі, під які підставляється будь-яка твоя текстура.
        try {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(modelKey));
            meta.setCustomModelDataComponent(cmd);
        } catch (Throwable ignored) {
            // Старіше API без CustomModelDataComponent — предмет лишається валідним.
        }
        item.setItemMeta(meta);
        ItemStack built = new NBTBuilder(item).setString(NBT_COIN, coinType).build();
        DiscItems.stripJukeboxPlayable(built);
        return built;
    }

    public boolean isPound(ItemStack item) {
        return isCoin(item, COIN_POUND);
    }

    public boolean isCoppet(ItemStack item) {
        return isCoin(item, COIN_COPPET);
    }

    private boolean isCoin(ItemStack item, String coinType) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (!NBTBuilder.hasKey(item, NBT_COIN, PersistentDataType.STRING)) {
            return false;
        }
        Optional<String> value = new NBTBuilder(item).getString(item, NBT_COIN);
        return value.map(coinType::equals).orElse(false);
    }
}
