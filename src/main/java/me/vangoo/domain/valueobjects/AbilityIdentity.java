package me.vangoo.domain.valueobjects;

import java.util.Objects;

public record AbilityIdentity(String id) {

    public AbilityIdentity {
        Objects.requireNonNull(id, "Ability ID cannot be null");
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("Ability ID cannot be empty");
        }
    }

    /**
     * Create identity from string ID
     *
     * @param id Unique identifier (e.g., "scan_gaze")
     */
    public static AbilityIdentity of(String id) {
        return new AbilityIdentity(id.toLowerCase().trim());
    }

    /**
     * Check if this ability can replace another based on identity
     *
     * @param other Other ability identity
     * @return true if they represent the same conceptual ability
     */
    public boolean canReplace(AbilityIdentity other) {
        return this.equals(other);
    }

    @Override
    public String toString() {
        return id;
    }
}