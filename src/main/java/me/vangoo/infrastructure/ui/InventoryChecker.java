package me.vangoo.infrastructure.ui;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.List;

public class InventoryChecker {

    /**
     * Повертає загальну кількість предметів (включаючи контейнери: Shulker Box та Bundle)
     */
    public static int getTotalAmount(Player player, ItemStack targetItem) {
        if (targetItem == null || targetItem.getType() == Material.AIR) return 0;

        // Отримуємо весь вміст інвентарю (включно з бронею та лівою рукою)
        return countItem(player.getInventory().getContents(), targetItem);
    }

    /**
     * Рекурсивний підрахунок
     */
    private static int countItem(ItemStack[] items, ItemStack targetItem) {
        int count = 0;
        if (items == null) return 0;

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            // 1. Перевіряємо, чи це сам предмет
            if (item.isSimilar(targetItem)) {
                count += item.getAmount();
            }

            // 2. Якщо це Shulker Box - заглядаємо всередину
            if (isShulkerBox(item)) {
                if (item.getItemMeta() instanceof BlockStateMeta meta) {
                    if (meta.getBlockState() instanceof ShulkerBox shulkerBox) {
                        count += countItem(shulkerBox.getInventory().getContents(), targetItem);
                    }
                }
            }

            // 3. Якщо це Bundle (Мішечок) - заглядаємо всередину
            // Важливо: перевіряємо instanceof BundleMeta, бо Material.BUNDLE з'явився лише у нових версіях
            if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
                List<ItemStack> bundleContent = bundleMeta.getItems();
                // Перетворюємо List у Array для рекурсивного виклику
                count += countItem(bundleContent.toArray(new ItemStack[0]), targetItem);
            }
        }
        return count;
    }

    /**
     * Перевіряє, чи є предмет шалкером (враховуючи всі кольори)
     */
    private static boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("SHULKER_BOX");
    }
}