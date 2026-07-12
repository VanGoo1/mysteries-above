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

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * GUI-пікер цілого числа в межах [min, max] кнопками +/- замість чат-вводу.
 * Для кількості штук на ринку (1–64).
 */
public class AmountPicker {

    private final Plugin plugin;

    public AmountPicker(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param min       мінімальне (і стартове) значення; має бути ≥ 1 для кількості штук.
     * @param onConfirm приймає обране число (у головному потоці).
     * @param onCancel  дія на «Скасувати» (напр. назад у попереднє меню).
     */
    public void open(Player player, String title, int min, int max,
                     IntConsumer onConfirm, Runnable onCancel) {
        new Session(player, title, min, max, onConfirm, onCancel).render();
    }

    private final class Session {
        private static final int SLOT_DISPLAY_ROW = 2, SLOT_DISPLAY_COL = 5;

        private final Player player;
        private final String title;
        private final int min;
        private final int max;
        private final IntConsumer onConfirm;
        private final Runnable onCancel;

        private int value;
        private Gui gui;

        Session(Player player, String title, int min, int max,
                IntConsumer onConfirm, Runnable onCancel) {
            this.player = player;
            this.title = title;
            this.min = min;
            this.max = max;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
            this.value = min;
        }

        void render() {
            gui = Gui.gui()
                    .title(Component.text(title).color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                    .rows(3)
                    .disableAllInteractions()
                    .create();
            gui.getFiller().fill(new GuiItem(filler()));

            gui.setItem(1, 4, step(Material.LIME_STAINED_GLASS_PANE, "+10", 10));
            gui.setItem(1, 6, step(Material.LIME_STAINED_GLASS_PANE, "+1", 1));
            gui.setItem(3, 4, step(Material.RED_STAINED_GLASS_PANE, "-10", -10));
            gui.setItem(3, 6, step(Material.RED_STAINED_GLASS_PANE, "-1", -1));

            gui.setItem(SLOT_DISPLAY_ROW, SLOT_DISPLAY_COL, new GuiItem(displayStack(), e -> e.setCancelled(true)));
            gui.setItem(2, 1, new GuiItem(button(Material.LIME_CONCRETE, ChatColor.GREEN + "✔ Підтвердити"), e -> {
                e.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                player.closeInventory();
                int chosen = value;
                Bukkit.getScheduler().runTask(plugin, () -> onConfirm.accept(chosen));
            }));
            gui.setItem(2, 9, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "✘ Скасувати"), e -> {
                e.setCancelled(true);
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, onCancel);
            }));
            gui.open(player);
        }

        private GuiItem step(Material material, String name, int delta) {
            return new GuiItem(button(material, ChatColor.YELLOW + name), e -> {
                e.setCancelled(true);
                int next = Math.max(min, Math.min(max, value + delta));
                if (next == value) {
                    return;
                }
                value = next;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, delta > 0 ? 1.3f : 0.9f);
                gui.updateItem(SLOT_DISPLAY_ROW, SLOT_DISPLAY_COL, displayStack());
                gui.update();
            });
        }

        private ItemStack displayStack() {
            return button(Material.PAPER, ChatColor.AQUA + "Кількість: " + ChatColor.WHITE + "×" + value,
                    ChatColor.DARK_GRAY + "(" + min + "–" + max + ")");
        }
    }

    private ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
