package me.vangoo.presentation.listeners;

import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Presentation Listener: Handles recipe book interactions
 */
public class RecipeBookInteractionListener implements Listener {
    private final RecipeBookFactory recipeBookFactory;
    private final RecipeUnlockService recipeUnlockService;

    public RecipeBookInteractionListener(
            RecipeBookFactory recipeBookFactory,
            RecipeUnlockService recipeUnlockService) {
        this.recipeBookFactory = recipeBookFactory;
        this.recipeUnlockService = recipeUnlockService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if it's a recipe book
        if (item == null || !recipeBookFactory.isRecipeBook(item)) {
            return;
        }

        event.setCancelled(true);

        // Get recipe data
        String pathwayName = recipeBookFactory.getPathwayName(item).orElse(null);
        Integer sequence = recipeBookFactory.getSequence(item).orElse(null);

        if (pathwayName == null || sequence == null) {
            player.sendMessage(ChatColor.RED + "Пошкоджена книжка рецептів!");
            return;
        }

        // Check if already unlocked
        if (recipeUnlockService.canCraftPotion(player.getUniqueId(), pathwayName, sequence)) {
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.YELLOW + "Ви вже знаєте цей рецепт!")
            );
            return;
        }

        // Unlock recipe
        boolean unlocked = recipeUnlockService.unlockRecipe(
                player.getUniqueId(),
                pathwayName,
                sequence
        );

        if (!unlocked) {
            player.sendMessage(ChatColor.RED + "Не вдалося розблокувати рецепт!");
            return;
        }

        // Remove book from inventory
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        // Show success effects
        player.sendMessage(ChatColor.GREEN + "✓ Рецепт розблоковано!");

        // Particle effects
        player.getWorld().spawnParticle(
                Particle.ENCHANT,
                player.getLocation().add(0, 1.5, 0),
                30,
                0.5, 0.5, 0.5,
                0.1
        );

        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1, 0),
                15,
                0.3, 0.5, 0.3,
                0.05
        );

        // Sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
    }
}