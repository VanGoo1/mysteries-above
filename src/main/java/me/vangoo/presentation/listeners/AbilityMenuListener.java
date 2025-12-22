package me.vangoo.presentation.listeners;

import me.vangoo.application.abilities.AbilityExecutionResult;
import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.RampageEffectsHandler;
import me.vangoo.domain.abilities.core.Ability;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.NBTBuilder;
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



/**
 * Listener for ability menu interactions.
 * Handles menu opening, ability selection, and item management.
 */
public class AbilityMenuListener implements Listener {
    private final AbilityMenu abilityMenu;
    private final BeyonderService beyonderService;
    private final AbilityExecutor abilityExecutor;
    private final AbilityItemFactory abilityItemFactory;
    private final RampageEffectsHandler effectsHandler;

    public AbilityMenuListener(
            AbilityMenu abilityMenu,
            BeyonderService beyonderService,
            AbilityExecutor abilityExecutor,
            AbilityItemFactory abilityItemFactory,
            RampageEffectsHandler effectsHandler
    ) {
        this.abilityMenu = abilityMenu;
        this.beyonderService = beyonderService;
        this.abilityExecutor = abilityExecutor;
        this.abilityItemFactory = abilityItemFactory;
        this.effectsHandler = effectsHandler;
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

        // Execute passive abilities immediately
        if (ability.isPassive()) {
            executePassiveAbility(player, beyonder, ability);
        } else {
            // Give active ability item to player
            player.getInventory().addItem(abilityItem);
        }
    }

    /**
     * Execute passive ability when selected from menu
     */
    private void executePassiveAbility(Player player, Beyonder beyonder, Ability ability) {

        AbilityExecutionResult result = abilityExecutor.execute(beyonder, ability);

        if (!result.isSuccess()) {
            player.sendMessage("§c" + result.message());

            if (result.hasSanityPenalty()) {
                effectsHandler.showSanityLossEffects(player, beyonder, result.sanityCheck());
            }
        }

        beyonderService.updateBeyonder(beyonder);
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

        if (!abilityItemFactory.isAbilityItem(cursor, beyonder)) {
            return;
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.PLACE_ALL ||
                action == InventoryAction.PLACE_ONE ||
                action == InventoryAction.PLACE_SOME) {

            if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
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
}