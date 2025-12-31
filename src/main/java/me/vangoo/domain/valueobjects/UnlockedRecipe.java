package me.vangoo.domain.valueobjects;

import java.util.Objects;

/**
 * Domain Value Object: Represents an unlocked potion recipe for a player
 */
public record UnlockedRecipe(String pathwayName, int sequence) {

    public UnlockedRecipe {
        Objects.requireNonNull(pathwayName, "Pathway name cannot be null");
        if (pathwayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Pathway name cannot be empty");
        }
        if (sequence < 0 || sequence > 9) {
            throw new IllegalArgumentException("Sequence must be between 0 and 9, got: " + sequence);
        }
    }

    /**
     * Create unlocked recipe
     */
    public static UnlockedRecipe of(String pathwayName, int sequence) {
        return new UnlockedRecipe(pathwayName.trim(), sequence);
    }

    /**
     * Get unique identifier for this recipe
     */
    public String getRecipeId() {
        return pathwayName + "_" + sequence;
    }

    @Override
    public String toString() {
        return String.format("%s Sequence %d", pathwayName, sequence);
    }
}