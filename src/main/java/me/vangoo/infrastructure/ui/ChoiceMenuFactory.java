package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ChoiceMenuFactory {

    public static <T> void openChoiceMenu(
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
            T choice = choices.get(i);
            ItemStack item = itemMapper.apply(choice);

            GuiItem guiItem = new GuiItem(item, event -> {
                event.setCancelled(true);
                player.closeInventory();
                onSelect.accept(choice);
            });

            gui.setItem(i, guiItem);
        }

        gui.open(player);
    }
}