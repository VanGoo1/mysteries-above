package me.vangoo.infrastructure.recipes;

import me.vangoo.application.services.CustomItemService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Infrastructure: Manages crafting recipe for Master Recipe Book
 */
public class RecipeBookCraftingRecipe {
    private final Plugin plugin;
    private final CustomItemService customItemService;
    private final Logger logger;

    private NamespacedKey recipeKey;

    public RecipeBookCraftingRecipe(Plugin plugin, CustomItemService customItemService) {
        this.plugin = plugin;
        this.customItemService = customItemService;
        this.logger = plugin.getLogger();
    }

    /**
     * Register the crafting recipe for Master Recipe Book
     */
    public void registerRecipe() {
        Optional<ItemStack> bookOpt = customItemService.createItemStack("master_recipe_book");

        if (bookOpt.isEmpty()) {
            logger.warning("Failed to create master_recipe_book item! Recipe not registered.");
            return;
        }

        ItemStack result = bookOpt.get();

        // Create recipe key
        recipeKey = new NamespacedKey(plugin, "master_recipe_book");

        // Create shaped recipe
        // Pattern:
        //   P P P
        //   P B P
        //   P P P
        // P = Paper, B = Book
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape("PPP", "PBP", "PPP");
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('B', Material.BOOK);

        // Register recipe
        try {
            Bukkit.addRecipe(recipe);
            logger.info("Master Recipe Book crafting recipe registered successfully");
        } catch (Exception e) {
            logger.severe("Failed to register Master Recipe Book recipe: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Unregister the recipe (for cleanup)
     */
    public void unregisterRecipe() {
        if (recipeKey != null) {
            Bukkit.removeRecipe(recipeKey);
            logger.info("Master Recipe Book recipe unregistered");
        }
    }
}