package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Application Service: Handles potion crafting logic
 */
public class PotionCraftingService {
    private final PotionManager potionManager;
    private final RecipeUnlockService recipeUnlockService;
    private final CustomItemService customItemService;

    public PotionCraftingService(
            PotionManager potionManager,
            RecipeUnlockService recipeUnlockService,
            CustomItemService customItemService) {
        this.potionManager = potionManager;
        this.recipeUnlockService = recipeUnlockService;
        this.customItemService = customItemService;
    }

    /**
     * Result of crafting attempt
     */
    public record CraftingResult(
            boolean success,
            String pathwayName,
            int sequence,
            ItemStack potion,
            String message
    ) {
        public static CraftingResult success(String pathwayName, int sequence, ItemStack potion) {
            return new CraftingResult(true, pathwayName, sequence, potion, "Зілля успішно створено!");
        }

        public static CraftingResult failure(String message) {
            return new CraftingResult(false, null, -1, null, message);
        }
    }

    /**
     * Try to craft potion from ingredients
     */
    public CraftingResult trycraft(UUID playerId, List<ItemStack> thrownItems) {
        if (thrownItems.isEmpty()) {
            return CraftingResult.failure("Недостатньо інгредієнтів");
        }

        // Try all pathway potions
        for (PathwayPotions pathwayPotions : potionManager.getPotions()) {
            String pathwayName = pathwayPotions.getPathway().getName();

            // Try each sequence
            for (int sequence = 9; sequence >= 0; sequence--) {
                ItemStack[] requiredIngredients = pathwayPotions.getIngredients(sequence);

                if (requiredIngredients == null || requiredIngredients.length == 0) {
                    continue;
                }

                // Check if player has unlocked this recipe
                if (!recipeUnlockService.canCraftPotion(playerId, pathwayName, sequence)) {
                    continue;
                }

                // Check if ingredients match
                if (ingredientsMatch(thrownItems, requiredIngredients)) {
                    // Create potion
                    ItemStack potion = potionManager.createPotionItem(
                            pathwayName,
                            Sequence.of(sequence)
                    );

                    return CraftingResult.success(pathwayName, sequence, potion);
                }
            }
        }

        return CraftingResult.failure("Інгредієнти не підходять для жодного рецепту");
    }

    /**
     * Check if thrown items match required ingredients
     */
    private boolean ingredientsMatch(List<ItemStack> thrownItems, ItemStack[] requiredIngredients) {
        // Create maps of item counts
        Map<String, Integer> thrownCounts = countItems(thrownItems);
        Map<String, Integer> requiredCounts = countItems(Arrays.asList(requiredIngredients));

        // Must have exact match
        if (thrownCounts.size() != requiredCounts.size()) {
            return false;
        }

        for (Map.Entry<String, Integer> entry : requiredCounts.entrySet()) {
            String itemKey = entry.getKey();
            int requiredAmount = entry.getValue();
            int thrownAmount = thrownCounts.getOrDefault(itemKey, 0);

            if (thrownAmount != requiredAmount) {
                return false;
            }
        }

        return true;
    }

    /**
     * Count items by type (handles both regular items and custom items)
     */
    private Map<String, Integer> countItems(List<ItemStack> items) {
        Map<String, Integer> counts = new HashMap<>();

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            String itemKey = getItemKey(item);
            counts.merge(itemKey, item.getAmount(), Integer::sum);
        }

        return counts;
    }

    /**
     * Get unique key for item (handles custom items)
     */
    private String getItemKey(ItemStack item) {
        // Check if it's a custom item
        if (customItemService.isCustomItem(item)) {
            return customItemService.getCustomItem(item)
                    .map(customItem -> "custom:" + customItem.id())
                    .orElse("vanilla:" + item.getType().name());
        }

        // Regular Minecraft item
        return "vanilla:" + item.getType().name();
    }
}