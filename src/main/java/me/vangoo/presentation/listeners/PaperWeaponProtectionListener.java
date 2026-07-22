package me.vangoo.presentation.listeners;

import me.vangoo.pathways.fool.abilities.PaperWeaponry;
import org.bukkit.ChatColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Захист паперової зброї Фокусника ({@link PaperWeaponry}) від переробки в ресурс.
 *
 * <p>Зброя стоїть на ванільних матеріалах ({@code BLAZE_ROD} / {@code STICK} /
 * {@code BRICK}), тож без цього гейта «Паперова бита» крафтилась у вогняний порошок,
 * а бита/тростина горіли в печі як паливо — 32 паперу перетворювались на ресурс,
 * якого шлях Блазня давати не мусить.
 *
 * <p>Правило одне й просте: <b>предмет із NBT паперової зброї не покидає інвентар
 * гравця</b>. Крафт (у сітці 2x2 і за столом) не дає результату; будь-яке
 * переміщення в чужий контейнер (піч, варильна стійка, ковадло, точило, скриня,
 * торгівля) скасовується — разом із воронками, щоб покладену на землю зброю не
 * можна було засмоктати в піч в обхід GUI.
 */
public class PaperWeaponProtectionListener implements Listener {

    private static final String DENY_MESSAGE =
            ChatColor.RED + "✗ Паперова зброя розсипається від будь-якої обробки";

    // ── Крафт ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (matrixHasPaperWeapon(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    /** Друга засувка: рецепт міг прийти від іншого плагіна повз PrepareItemCraftEvent. */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (matrixHasPaperWeapon(event.getInventory().getMatrix())) {
            deny(event.getWhoClicked());
            event.setCancelled(true);
        }
    }

    // ── Переміщення в чужі інвентарі ─────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        // Власний інвентар (у т.ч. сітка 2x2) — рухати зброю вільно; крафт ловить onPrepareCraft.
        if (isPlayerOwnInventory(top)) return;

        if (event.getClick() == ClickType.NUMBER_KEY && event.getClickedInventory() == top) {
            ItemStack swapped = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (isPaperWeapon(swapped)) cancel(event, event.getWhoClicked());
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND && event.getClickedInventory() == top) {
            if (isPaperWeapon(event.getWhoClicked().getInventory().getItemInOffHand())) {
                cancel(event, event.getWhoClicked());
            }
            return;
        }
        // Shift-клік із нижнього інвентаря перекидає предмет саме у верхній.
        if (event.isShiftClick() && event.getClickedInventory() == view.getBottomInventory()
                && isPaperWeapon(event.getCurrentItem())) {
            cancel(event, event.getWhoClicked());
            return;
        }
        if (event.getClickedInventory() == top && isPaperWeapon(event.getCursor())) {
            cancel(event, event.getWhoClicked());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (isPlayerOwnInventory(top)) return;
        if (!isPaperWeapon(event.getOldCursor())) return;

        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                cancel(event, event.getWhoClicked());
                return;
            }
        }
    }

    // ── Воронки: обхід GUI через землю ───────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (isPaperWeapon(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isPaperWeapon(event.getItem().getItemStack())) event.setCancelled(true);
    }

    // ── Хелпери ──────────────────────────────────────────────────────────────

    private boolean matrixHasPaperWeapon(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (isPaperWeapon(item)) return true;
        }
        return false;
    }

    private boolean isPaperWeapon(ItemStack item) {
        return PaperWeaponry.weaponType(item) != null;
    }

    private boolean isPlayerOwnInventory(Inventory top) {
        InventoryType type = top.getType();
        return type == InventoryType.CRAFTING || type == InventoryType.PLAYER;
    }

    private void cancel(org.bukkit.event.Cancellable event, HumanEntity who) {
        event.setCancelled(true);
        deny(who);
    }

    private void deny(HumanEntity who) {
        if (who instanceof Player player) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(DENY_MESSAGE));
        }
    }
}
