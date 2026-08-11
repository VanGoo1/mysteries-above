package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.UnlockedRecipe;
import me.vangoo.infrastructure.IRecipeUnlockRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Application Service: Manages recipe unlocking for players
 */
public class RecipeUnlockService {
    private final IRecipeUnlockRepository repository;

    public RecipeUnlockService(IRecipeUnlockRepository repository) {
        this.repository = repository;
    }

    /**
     * Unlock a recipe for a player
     */
    public boolean unlockRecipe(UUID playerId, String pathwayName, int sequence) {
        UnlockedRecipe recipe = UnlockedRecipe.of(pathwayName, sequence);
        return repository.unlockRecipe(playerId, recipe);
    }

    /**
     * Take a recipe away from a player (Death Seq 7: the recipe moves, it is not copied)
     */
    public boolean revokeRecipe(UUID playerId, String pathwayName, int sequence) {
        return repository.revokeRecipe(playerId, UnlockedRecipe.of(pathwayName, sequence));
    }

    /**
     * Check if player can craft this potion
     */
    public boolean canCraftPotion(UUID playerId, String pathwayName, int sequence) {
        return repository.hasUnlockedRecipe(playerId, pathwayName, sequence);
    }

    /**
     * Get all unlocked recipes for player
     */
    public Set<UnlockedRecipe> getUnlockedRecipes(UUID playerId) {
        return repository.getUnlockedRecipes(playerId);
    }
}