package me.vangoo.application.services;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application Service: Manages passive abilities lifecycle
 * <p>
 * Responsibilities:
 * - Track which passive abilities are active for each player
 * - Provide tick execution for toggleable and permanent passives
 * - Manage ability state across player sessions
 */
public class PassiveAbilityManager {
    private final Map<UUID, Set<AbilityIdentity>> enabledToggleableAbilities;
    // Track enabled toggleable abilities per player {playerId -> Set<abilityName>}
    private final Map<UUID, Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility>>
            activePassiveInstances = new ConcurrentHashMap<>();

    public PassiveAbilityManager() {
        this.enabledToggleableAbilities = new ConcurrentHashMap<>();
    }

    /**
     * Register a player's abilities when they join or advance sequence
     */
    public void registerPlayer(UUID playerId, List<Ability> abilities) {
        // Initialize toggleable abilities set if not exists
        enabledToggleableAbilities.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // Build or update the per-player permanent instances map
        Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> perPlayer =
                activePassiveInstances.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        // Fill with the instances from the provided abilities list (prefer provided instance)
        for (Ability ability : abilities) {
            if (ability instanceof PermanentPassiveAbility perm) {
                perPlayer.computeIfAbsent(perm.getClass(), cls -> perm);
            }
        }
    }

    /**
     * Unregister player when they leave
     */
    public void unregisterPlayer(UUID playerId) {
        enabledToggleableAbilities.remove(playerId);
        // Remove stored permanent instances and allow GC -> but call cleanUp before removing if needed
        Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> removed = activePassiveInstances.remove(playerId);
        if (removed != null) {
            removed.values().forEach(PermanentPassiveAbility::cleanUp);
        }
    }

    /**
     * Toggle a toggleable passive ability on/off.
     * Returns the new state and calls appropriate lifecycle hooks.
     *
     * @param playerId Player UUID
     * @param ability  The ability to toggle
     * @param context  Context for calling lifecycle hooks
     * @return true if now enabled, false if now disabled
     */
    public boolean toggleAbility(UUID playerId, ToggleablePassiveAbility ability, IAbilityContext context) {
        AbilityIdentity abilityIdentity = ability.getIdentity();

        // Use atomic compute operation to avoid race conditions
        boolean[] result = new boolean[1];
        enabledToggleableAbilities.compute(playerId, (id, enabled) -> {
            if (enabled == null) {
                enabled = ConcurrentHashMap.newKeySet();
            }

            boolean wasEnabled = enabled.contains(abilityIdentity);

            if (wasEnabled) {
                // Disable
                enabled.remove(abilityIdentity);
                ability.onDisable(context);
                result[0] = false;
            } else {
                // Enable
                enabled.add(abilityIdentity);
                ability.onEnable(context);
                result[0] = true;
            }

            return enabled.isEmpty() ? null : enabled;
        });

        return result[0];
    }

    /**
     * Check if a toggleable ability is enabled for a player
     *
     * @param playerId        Player UUID
     * @param abilityIdentity Ability name
     * @return true if enabled, false otherwise
     */
    public boolean isToggleableEnabled(UUID playerId, AbilityIdentity abilityIdentity) {
        Set<AbilityIdentity> enabled = enabledToggleableAbilities.get(playerId);
        return enabled != null && enabled.contains(abilityIdentity);
    }

    /**
     * Get all toggleable passives that should tick for this player
     */
    public List<ToggleablePassiveAbility> getActiveToggleables(Beyonder beyonder) {
        Set<AbilityIdentity> enabled = enabledToggleableAbilities.get(beyonder.getPlayerId());
        if (enabled == null || enabled.isEmpty()) {
            return Collections.emptyList();
        }

        return beyonder.getAbilities().stream()
                .filter(ability -> ability instanceof ToggleablePassiveAbility)
                .map(ability -> (ToggleablePassiveAbility) ability)
                .filter(ability -> enabled.contains(ability.getIdentity()))
                .collect(Collectors.toList());
    }

    /**
     * Get all permanent passives for this player
     */
    public List<PermanentPassiveAbility> getPermanentPassives(Beyonder beyonder) {
        return beyonder.getAbilities().stream()
                .filter(ability -> ability instanceof PermanentPassiveAbility)
                .map(ability -> (PermanentPassiveAbility) ability)
                .collect(Collectors.toList());
    }

    /**
     * Execute tick for all active passive abilities.
     * Called by scheduler every tick.
     *
     * @param beyonder Player's beyonder
     * @param context  Ability execution context
     */
    public void tickPlayer(Beyonder beyonder, IAbilityContext context) {
        if (beyonder == null) {
            return;
        }

        UUID playerId = beyonder.getPlayerId();

        // Tick enabled toggleable passives (unchanged)
        List<ToggleablePassiveAbility> toggleables = getActiveToggleables(beyonder);
        for (ToggleablePassiveAbility ability : toggleables) {
            try {
                ability.tick(context);
            } catch (Exception e) {
                System.err.println("Error ticking toggleable ability " +
                        ability.getName() + " for player " + playerId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // NEW: Tick only SINGLE instances of each PermanentPassiveAbility class for this player
        Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> playerPassives =
                activePassiveInstances.computeIfAbsent(playerId, id -> {
                    // lazy-init from beyonder abilities if not present
                    Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> map = new ConcurrentHashMap<>();
                    List<PermanentPassiveAbility> permanents = getPermanentPassives(beyonder);
                    for (PermanentPassiveAbility p : permanents) {
                        map.putIfAbsent(p.getClass(), p);
                    }
                    return map;
                });

        for (PermanentPassiveAbility ability : playerPassives.values()) {
            try {
                ability.tick(context);
            } catch (Exception e) {
                System.err.println("Error ticking permanent ability " +
                        ability.getName() + " for player " + playerId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Activate all permanent passives for a player (called on join).
     * Uses stored singleton instances.
     */
    public void activatePermanentPassives(Beyonder beyonder, IAbilityContext context) {
        if (beyonder == null) return;
        UUID playerId = beyonder.getPlayerId();

        // ensure map exists
        Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> playerPassives =
                activePassiveInstances.computeIfAbsent(playerId, id -> {
                    Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> map = new ConcurrentHashMap<>();
                    for (PermanentPassiveAbility p : getPermanentPassives(beyonder)) {
                        map.putIfAbsent(p.getClass(), p);
                    }
                    return map;
                });

        for (PermanentPassiveAbility ability : playerPassives.values()) {
            try {
                ability.onActivate(context);
            } catch (Exception e) {
                System.err.println("Error activating permanent ability " +
                        ability.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Deactivate all permanent passives for a player (called on quit or on refresh).
     * Calls onDeactivate on the stored instances.
     */
    public void deactivatePermanentPassives(Beyonder beyonder, IAbilityContext context) {
        if (beyonder == null) return;
        UUID playerId = beyonder.getPlayerId();

        Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> playerPassives =
                activePassiveInstances.get(playerId);
        if (playerPassives == null) return;

        for (PermanentPassiveAbility ability : playerPassives.values()) {
            try {
                ability.onDeactivate(context);
            } catch (Exception e) {
                System.err.println("Error deactivating permanent ability " +
                        ability.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Disable all toggleable passives for a player and call onDisable hooks.
     * Used during cleanup.
     */
    public void disableAllToggleables(Beyonder beyonder, IAbilityContext context) {
        UUID playerId = beyonder.getPlayerId();
        Set<AbilityIdentity> enabled = enabledToggleableAbilities.get(playerId);
        if (enabled == null || enabled.isEmpty()) {
            return;
        }

        List<ToggleablePassiveAbility> toDisable = beyonder.getAbilities().stream()
                .filter(ability -> ability instanceof ToggleablePassiveAbility)
                .map(ability -> (ToggleablePassiveAbility) ability)
                .filter(ability -> enabled.contains(ability.getIdentity()))
                .toList();

        for (ToggleablePassiveAbility ability : toDisable) {
            try {
                ability.onDisable(context);
            } catch (Exception e) {
                System.err.println("Error disabling toggleable ability " +
                        ability.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        enabled.clear();
    }

    /**
     * Cleanup all abilities for a player (called on quit or ability loss).
     * Calls lifecycle hooks and cleans up ability state.
     */
    public void cleanupPlayer(Beyonder beyonder, IAbilityContext context) {
        if (beyonder == null) {
            return;
        }

        UUID playerId = beyonder.getPlayerId();

        // Disable all toggleables
        disableAllToggleables(beyonder, context);

        // Deactivate all permanents (call hooks)
        deactivatePermanentPassives(beyonder, context);

        // Call cleanUp on stored singleton permanent instances
        Map<Class<? extends PermanentPassiveAbility>, PermanentPassiveAbility> playerPassives =
                activePassiveInstances.remove(playerId);
        if (playerPassives != null) {
            for (PermanentPassiveAbility ability : playerPassives.values()) {
                try {
                    ability.cleanUp();
                } catch (Exception e) {
                    System.err.println("Error cleaning up ability " +
                            ability.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Call cleanUp on any remaining abilities from beyonder (toggleables etc.)
        for (Ability ability : beyonder.getAbilities()) {
            try {
                ability.cleanUp();
            } catch (Exception e) {
                System.err.println("Error cleaning up ability " +
                        ability.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Unregister player
        unregisterPlayer(playerId);
    }

    /**
     * Get statistics about active passive abilities
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPlayers", enabledToggleableAbilities.size());
        stats.put("totalEnabledToggleables",
                enabledToggleableAbilities.values().stream()
                        .mapToInt(Set::size)
                        .sum());
        return stats;
    }
}