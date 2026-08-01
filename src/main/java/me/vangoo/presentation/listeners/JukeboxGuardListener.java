package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.items.DiscItems;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Не пускає предмети плагіну в програвач.
 *
 * <p>Усі предмети плагіну стоять на музичних пластинках (див. {@code .claude/rules/item-materials.md}),
 * а отже ванільно «програються». На новіших версіях це лікує зняття компонента
 * {@code jukebox_playable} у {@link DiscItems#stripJukeboxPlayable(ItemStack)}, але на 1.21.1
 * Paper-API для запису компонентів ({@code io.papermc.paper.datacomponent}) ще немає — той метод
 * там мовчки нічого не робить. Без цього лістенера інгредієнт, Характеристика чи монета зникали б
 * у jukebox'і.
 *
 * <p>Дві дірки, обидві закриті тут: гравець кладе диск рукою і хопер заштовхує диск у програвач.
 * Розпізнавання — по NBT ({@link NBTBuilder#isPluginItem}), ніколи по матеріалу: ванільні
 * пластинки мусять грати як завжди.
 */
public class JukeboxGuardListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInsert(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX) {
            return;
        }
        if (!isPluginDisc(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.GRAY + "Це не музика — програвач такого не приймає.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperInsert(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() != InventoryType.JUKEBOX) {
            return;
        }
        if (isPluginDisc(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean isPluginDisc(ItemStack item) {
        return item != null && DiscItems.isDisc(item.getType()) && NBTBuilder.isPluginItem(item);
    }
}
