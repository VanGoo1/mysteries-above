package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.market.PoundMoney;
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
import java.util.function.Consumer;

/**
 * GUI-пікер грошової суми ({@link PoundMoney}) кнопками +/- замість чат-вводу.
 * Фунти й коппети — окремі контроли, тож немає неоднозначності «15 — це фунти чи коппети?».
 * Стан сесії — усього коппетів (int); перевищення 19к само «перекочується» у фунти
 * (сума тримається в коппетах, {@link PoundMoney#format()} малює «2 ф 15 к»).
 */
public class MoneyPicker {

    private final Plugin plugin;

    public MoneyPicker(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param allowZero чи дозволено підтвердити нульову суму (буст у бартері — так; ціна — ні).
     * @param onConfirm приймає обрану суму (у головному потоці).
     * @param onCancel  дія на «Скасувати» (напр. назад у попереднє меню).
     */
    public void open(Player player, String title, boolean allowZero,
                     Consumer<PoundMoney> onConfirm, Runnable onCancel) {
        new Session(player, title, allowZero, onConfirm, onCancel).render();
    }

    private final class Session {
        private static final int SLOT_CONFIRM_ROW = 2, SLOT_CONFIRM_COL = 1;
        private static final int SLOT_DISPLAY_ROW = 2, SLOT_DISPLAY_COL = 5;

        private final Player player;
        private final String title;
        private final boolean allowZero;
        private final Consumer<PoundMoney> onConfirm;
        private final Runnable onCancel;

        private int coppets = 0;
        private Gui gui;

        Session(Player player, String title, boolean allowZero,
                Consumer<PoundMoney> onConfirm, Runnable onCancel) {
            this.player = player;
            this.title = title;
            this.allowZero = allowZero;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        void render() {
            gui = Gui.gui()
                    .title(Component.text(title).color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                    .rows(3)
                    .disableAllInteractions()
                    .create();
            gui.getFiller().fill(new GuiItem(filler()));

            // Ряд +: фунти зліва, коппети справа.
            gui.setItem(1, 2, step(Material.LIME_STAINED_GLASS_PANE, "+10 фунтів", 10 * PoundMoney.COPPETS_PER_POUND));
            gui.setItem(1, 3, step(Material.LIME_STAINED_GLASS_PANE, "+1 фунт", PoundMoney.COPPETS_PER_POUND));
            gui.setItem(1, 7, step(Material.LIME_STAINED_GLASS_PANE, "+5 коппетів", 5));
            gui.setItem(1, 8, step(Material.LIME_STAINED_GLASS_PANE, "+1 коппет", 1));

            // Ряд -: симетрично.
            gui.setItem(3, 2, step(Material.RED_STAINED_GLASS_PANE, "-10 фунтів", -10 * PoundMoney.COPPETS_PER_POUND));
            gui.setItem(3, 3, step(Material.RED_STAINED_GLASS_PANE, "-1 фунт", -PoundMoney.COPPETS_PER_POUND));
            gui.setItem(3, 7, step(Material.RED_STAINED_GLASS_PANE, "-5 коппетів", -5));
            gui.setItem(3, 8, step(Material.RED_STAINED_GLASS_PANE, "-1 коппет", -1));

            gui.setItem(SLOT_DISPLAY_ROW, SLOT_DISPLAY_COL, new GuiItem(displayStack(), e -> e.setCancelled(true)));
            gui.setItem(SLOT_CONFIRM_ROW, SLOT_CONFIRM_COL, new GuiItem(confirmStack(), e -> {
                e.setCancelled(true);
                if (coppets == 0 && !allowZero) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                    player.sendMessage(ChatColor.RED + "Сума має бути більшою за нуль.");
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                player.closeInventory();
                PoundMoney chosen = PoundMoney.ofCoppets(coppets);
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
                int next = coppets + delta;
                if (next < 0) {
                    next = 0;
                }
                if (next == coppets) {
                    return;
                }
                coppets = next;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, delta > 0 ? 1.3f : 0.9f);
                gui.updateItem(SLOT_DISPLAY_ROW, SLOT_DISPLAY_COL, displayStack());
                gui.updateItem(SLOT_CONFIRM_ROW, SLOT_CONFIRM_COL, confirmStack());
                gui.update();
            });
        }

        private ItemStack displayStack() {
            PoundMoney current = PoundMoney.ofCoppets(coppets);
            return button(Material.GOLD_NUGGET, ChatColor.GOLD + "Сума: " + current.format(),
                    ChatColor.DARK_GRAY + "(" + coppets + " коппетів усього)");
        }

        private ItemStack confirmStack() {
            boolean ready = coppets > 0 || allowZero;
            Material material = ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
            String name = (ready ? ChatColor.GREEN : ChatColor.GRAY) + "✔ Підтвердити";
            return ready
                    ? button(material, name)
                    : button(material, name, ChatColor.RED + "Потрібна сума більша за нуль");
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
