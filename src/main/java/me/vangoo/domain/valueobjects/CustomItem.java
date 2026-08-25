package me.vangoo.domain.valueobjects;


import java.util.List;
import java.util.Objects;

/**
 * Domain Value Object: Represents a custom item definition
 */
public record CustomItem(
        String id,
        String displayName,
        String material,
        List<String> lore,
        boolean glow,
        String customModelData
) {
    public CustomItem {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(displayName, "Display name cannot be null");
        Objects.requireNonNull(material, "Material cannot be null");
        if (material.isBlank()) {
            throw new IllegalArgumentException("Material cannot be blank");
        }

        // Ensure lore is never null
        if (lore == null) {
            lore = List.of();
        } else {
            lore = List.copyOf(lore); // Immutable copy
        }
    }

    /**
     * Check if custom model data is set
     */
    public boolean hasCustomModelData() {
        return customModelData != null && !customModelData.isEmpty();
    }
}