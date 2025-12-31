package me.vangoo.infrastructure;

import me.vangoo.domain.valueobjects.UnlockedRecipe;

import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure Interface: Repository for unlocked recipes
 */
public interface IRecipeUnlockRepository {

    /**
     * Unlock recipe for player
     */
    boolean unlockRecipe(UUID playerId, UnlockedRecipe recipe);

    /**
     * Check if player has unlocked recipe
     */
    boolean hasUnlockedRecipe(UUID playerId, String pathwayName, int sequence);

    /**
     * Get all unlocked recipes for player
     */
    Set<UnlockedRecipe> getUnlockedRecipes(UUID playerId);

    /**
     * Save all data to persistent storage
     */
    void saveAll();
}