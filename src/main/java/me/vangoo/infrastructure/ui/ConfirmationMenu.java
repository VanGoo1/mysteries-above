package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Одноразове підтвердження необоротної дії: «віддаєте X ↔ отримуєте Y», Підтвердити/Скасувати. */
public class ConfirmationMenu {

    private final Plugin plugin;

    public ConfirmationMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ItemStack give, ItemStack get, String title, Runnable onConfirm) {
        Gui gui = Gui.gui()
                .title(Component.text(title).color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 3, new GuiItem(labeled(give.clone(), ChatColor.RED + "Ви віддаєте")));
        gui.setItem(2, 5, new GuiItem(labeled(get.clone(), ChatColor.GREEN + "Ви отримаєте")));
        gui.setItem(2, 1, new GuiItem(button(Material.LIME_CONCRETE, ChatColor.GREEN + "✔ Підтвердити"), e -> {
            e.setCancelled(true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, onConfirm);
        }));
        gui.setItem(2, 9, new GuiItem(button(Material.RED_CONCRETE, ChatColor.RED + "✘ Скасувати"), e -> {
            e.setCancelled(true);
            player.closeInventory();
        }));
        gui.open(player);
    }

    private ItemStack labeled(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name + ChatColor.GRAY + ": "
                    + (meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name().toLowerCase()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of());
        item.setItemMeta(meta);
        return item;
    }
}
