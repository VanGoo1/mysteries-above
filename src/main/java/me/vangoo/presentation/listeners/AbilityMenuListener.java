package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.abilities.core.Ability;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import java.util.logging.Logger;


/**
 * Listener for ability menu interactions.
 * Handles menu opening, ability selection, and item management.
 */
public class AbilityMenuListener implements Listener {
    private final AbilityMenu abilityMenu;
    private final BeyonderService beyonderService;
    private final AbilityExecutor abilityExecutor;
    private final AbilityItemFactory abilityItemFactory;
    private final Logger logger;

    public AbilityMenuListener(
            AbilityMenu abilityMenu,
            BeyonderService beyonderService,
            AbilityExecutor abilityExecutor,
            AbilityItemFactory abilityItemFactory,
            Logger logger
    ) {
        this.abilityMenu = abilityMenu;
        this.beyonderService = beyonderService;
        this.abilityExecutor = abilityExecutor;
        this.abilityItemFactory = abilityItemFactory;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }
        abilityMenu.giveAbilityMenuItemToPlayer(player);
    }

    /**
     * Handle click on menu item or ability item in menu
     */
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) {
            return;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        // Handle click on menu item
        if (abilityMenu.isAbilityMenu(currentItem)) {
            handleMenuItemClick(event, player, beyonder);
            return;
        }

        // Handle click on ability item in menu
        if (abilityMenu.isAbilityItemInMenu(currentItem)) {
            handleAbilityItemInMenuClick(event, player, beyonder, currentItem);
        }
    }

    /**
     * Handle click on the menu item itself (opens menu)
     */
    private void handleMenuItemClick(InventoryClickEvent event, Player player, Beyonder beyonder) {
        event.setCancelled(true);

        // Drop any item on cursor
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            player.getWorld().dropItemNaturally(player.getLocation(), cursor);
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }

        // Open menu
        abilityMenu.openMenu(player, beyonder);
    }

    /**
     * Handle click on ability item inside the menu
     */
    private void handleAbilityItemInMenuClick(
            InventoryClickEvent event,
            Player player,
            Beyonder beyonder,
            ItemStack menuItem
    ) {
        event.setCancelled(true);

        // Remove menu marker from item
        ItemStack abilityItem = new NBTBuilder(menuItem)
                .remove("isInAbilityMenu")
                .build();

        // Check if player already has this item
        if (player.getInventory().contains(abilityItem)) {
            return;
        }

        // Get ability
        Ability ability = abilityItemFactory.getAbilityFromItem(abilityItem, beyonder);
        if (ability == null) {
            return;
        }

        switch (ability.getType()) {
            case ACTIVE:
                // Give item to player
                player.getInventory().addItem(abilityItem);
                break;

            case TOGGLEABLE_PASSIVE:
                // Execute to toggle
                executePassiveAbility(player, beyonder, ability);
                break;

            case PERMANENT_PASSIVE:
                // Cannot interact with permanent passives
                player.sendMessage(ChatColor.YELLOW + "Ця здібність завжди активна");
                break;
        }
    }

    /**
     * Execute passive ability when selected from menu
     */
    private void executePassiveAbility(Player player, Beyonder beyonder, Ability ability) {
        AbilityResult result = abilityExecutor.execute(beyonder, ability);

        logger.info(result.toString());
        if (!result.isSuccess()) {
            player.sendMessage(ChatColor.RED + result.getMessage());
        } else {
            player.sendMessage(result.getMessage());
        }
    }

    /**
     * Prevent ability items from being moved in non-player inventory
     */
    @EventHandler
    public void onItemAbilityClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) {
            return;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        if (abilityItemFactory.isAbilityItem(currentItem, beyonder)) {
            if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent ability items from being placed in crafting slots
     */
    @EventHandler
    public void onAbilityItemPlace(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null) {
            return;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        boolean isAbilityItem = abilityItemFactory.isAbilityItem(cursor, beyonder);
        boolean isMenuitem = abilityMenu.isAbilityMenu(cursor);

        if (!isAbilityItem && !isMenuitem) {
            return;
        }

        InventoryAction action = event.getAction();

        // Заборона розміщення в crafting слоти
        if (action == InventoryAction.PLACE_ALL ||
                action == InventoryAction.PLACE_ONE ||
                action == InventoryAction.PLACE_SOME) {

            if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
                event.setCancelled(true);
                return;
            }

            // Заборона в offhand (slot 40)
            if (event.getSlot() == 40) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent dropping menu and ability items
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        // Prevent dropping menu item
        if (abilityMenu.isAbilityMenu(item)) {
            event.setCancelled(true);
            return;
        }

        // Remove dropped ability items
        Beyonder beyonder = beyonderService.getBeyonder(event.getPlayer().getUniqueId());
        if (beyonder == null) {
            return;
        }

        if (abilityItemFactory.isAbilityItem(item, beyonder)) {
            event.getItemDrop().remove();
        }
    }

    /**
     * Remove menu and ability items from death drops
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Beyonder beyonder = beyonderService.getBeyonder(event.getEntity().getUniqueId());
        if (beyonder == null) {
            return;
        }

        event.getDrops().removeIf(item -> abilityMenu.isAbilityMenu(item) ||
                abilityItemFactory.isAbilityItem(item, beyonder));
    }

    /**
     * Give menu item back on respawn
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        if (beyonder.getSequenceLevel() != -1) {
            abilityMenu.giveAbilityMenuItemToPlayer(player);
        }
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        // Перевіряємо чи це ability item або menu item
        boolean mainIsAbility = mainHand != null &&
                (abilityItemFactory.isAbilityItem(mainHand, beyonder) || abilityMenu.isAbilityMenu(mainHand));
        boolean offIsAbility = offHand != null &&
                (abilityItemFactory.isAbilityItem(offHand, beyonder) || abilityMenu.isAbilityMenu(offHand));

        if (!mainIsAbility && !offIsAbility) {
            return;
        }

        if (player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    /**
     * Заборона розміщення ability items в рамки
     */
    @EventHandler
    public void onPlaceInItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame itemFrame)) {
            return;
        }

        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        itemFrame.getItem();
        if (itemFrame.getItem().getType() != Material.AIR) {
            return;
        }

        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        if (offHandItem.getType() != Material.AIR) {
            if (abilityItemFactory.isAbilityItem(offHandItem, beyonder)) {
                event.setCancelled(true);
                return;
            }

            if (abilityMenu.isAbilityMenu(offHandItem)) {
                event.setCancelled(true);
                return;
            }
        }

        if (mainHandItem.getType() != Material.AIR) {
            if (abilityItemFactory.isAbilityItem(mainHandItem, beyonder)) {
                event.setCancelled(true);
                return;
            }

            if (abilityMenu.isAbilityMenu(mainHandItem)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Розширена заборона переміщення через hotbar swap (цифри)
     */
    @EventHandler
    public void onHotbarSwap(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        // Заборона SWAP_OFFHAND для ability items
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && abilityItemFactory.isAbilityItem(cursor, beyonder)) {
                if (event.getSlot() == 40) { // 40 = offhand slot
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Не можна переміщувати здібності в ліву руку");
                }
            }
        }

        // Заборона hotbar swap (натискання цифр) для ability items в non-player інвентарях
        if (event.getAction() == InventoryAction.HOTBAR_SWAP ||
                event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {

            int hotbarButton = event.getHotbarButton();
            if (hotbarButton != -1) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);

                // Заборона swap ability item через hotbar в не-player інвентарі
                if (hotbarItem != null && abilityItemFactory.isAbilityItem(hotbarItem, beyonder)) {
                    if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // Заборона swap menu item через hotbar
                if (hotbarItem != null && abilityMenu.isAbilityMenu(hotbarItem)) {
                    if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}