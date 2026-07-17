package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Спільне меню вибору для здібностей. Малі списки отримують оздоблене меню:
 * скляна рамка по краях + рознесені (через слот) центровані предмети. Великі списки
 * (напр. пікер голів Перевтілення до 53 предметів) не влазять у рамку — для них
 * лишається щільне розкладання по слотах.
 */
public class ChoiceMenuFactory {

    private static final Material FRAME_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final int ITEMS_PER_ROW = 4;      // рознесено: макс 4 предмети в ряд (колонки через слот)
    private static final int MAX_INTERIOR_ROWS = 4;  // + рамка згори й знизу => до 6 рядів
    private static final int DECORATED_CAPACITY = ITEMS_PER_ROW * MAX_INTERIOR_ROWS; // 16

    // Центровані 0-індексовані колонки для k предметів у ряду (рознесені через порожній слот).
    private static final int[][] COLUMNS_BY_COUNT = {
            {},              // 0
            {4},             // 1
            {3, 5},          // 2
            {2, 4, 6},       // 3
            {1, 3, 5, 7}     // 4
    };

    public static <T> void openChoiceMenu(
            Player player,
            String title,
            List<T> choices,
            Function<T, ItemStack> itemMapper,
            Consumer<T> onSelect
    ) {
        if (choices.size() > DECORATED_CAPACITY) {
            openCompactMenu(player, title, choices, itemMapper, onSelect);
        } else {
            openDecoratedMenu(player, title, choices, itemMapper, onSelect);
        }
    }

    private static <T> void openDecoratedMenu(
            Player player,
            String title,
            List<T> choices,
            Function<T, ItemStack> itemMapper,
            Consumer<T> onSelect
    ) {
        int count = choices.size();
        int interiorRows = Math.max(1, (int) Math.ceil(count / (double) ITEMS_PER_ROW));

        Gui gui = Gui.gui()
                .title(Component.text(title))
                .rows(interiorRows + 2)
                .disableAllInteractions()
                .create();
        gui.getFiller().fill(new GuiItem(framePane()));

        int idx = 0;
        for (int row = 0; row < interiorRows; row++) {
            int remainingRows = interiorRows - row;
            int remainingItems = count - idx;
            int inThisRow = (int) Math.ceil(remainingItems / (double) remainingRows);
            int[] cols = COLUMNS_BY_COUNT[inThisRow];
            for (int c = 0; c < inThisRow; c++) {
                int slot = (row + 1) * 9 + cols[c];
                gui.setItem(slot, selectable(player, choices.get(idx++), itemMapper, onSelect));
            }
        }

        gui.open(player);
    }

    private static <T> void openCompactMenu(
            Player player,
            String title,
            List<T> choices,
            Function<T, ItemStack> itemMapper,
            Consumer<T> onSelect
    ) {
        Gui gui = Gui.gui()
                .title(Component.text(title))
                .rows(Math.min(6, (choices.size() + 8) / 9))
                .disableAllInteractions()
                .create();

        for (int i = 0; i < choices.size(); i++) {
            gui.setItem(i, selectable(player, choices.get(i), itemMapper, onSelect));
        }

        gui.open(player);
    }

    private static <T> GuiItem selectable(Player player, T choice,
                                          Function<T, ItemStack> itemMapper, Consumer<T> onSelect) {
        return new GuiItem(itemMapper.apply(choice), event -> {
            event.setCancelled(true);
            player.closeInventory();
            onSelect.accept(choice);
        });
    }

    private static ItemStack framePane() {
        ItemStack pane = new ItemStack(FRAME_MATERIAL);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
