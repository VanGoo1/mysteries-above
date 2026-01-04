package me.vangoo.presentation.listeners;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.application.services.PotionManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.RecipeBookMenu;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Presentation Listener: Handles Master Recipe Book interactions
 */
public class MasterRecipeBookListener implements Listener {
    private final Plugin plugin;
    private final CustomItemService customItemService;
    private final RecipeBookMenu recipeBookMenu;

    public MasterRecipeBookListener(
            Plugin plugin,
            CustomItemService customItemService,
            RecipeUnlockService recipeUnlockService,
            PotionManager potionManager,
            AbilityMenu abilityMenu) {
        this.plugin = plugin;
        this.customItemService = customItemService;
        this.recipeBookMenu = new RecipeBookMenu(
                plugin,
                recipeUnlockService,
                potionManager,
                abilityMenu
        );
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if it's the master recipe book
        if (item == null || !isMasterRecipeBook(item)) {
            return;
        }

        // Cancel the event
        event.setCancelled(true);

        // Play sound
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);

        // Open recipe menu
        recipeBookMenu.openMainMenu(player);
    }

    /**
     * Check if ItemStack is the Master Recipe Book
     */
    private boolean isMasterRecipeBook(ItemStack item) {
        Optional<CustomItem> customItemOpt = customItemService.getCustomItem(item);

        if (customItemOpt.isEmpty()) {
            return false;
        }

        CustomItem customItem = customItemOpt.get();
        return customItem.id().equals("master_recipe_book");
    }
}