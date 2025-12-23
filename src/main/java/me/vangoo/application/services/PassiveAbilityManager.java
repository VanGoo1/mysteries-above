package me.vangoo.application.services;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application Service: Manages passive abilities lifecycle
 *
 * Responsibilities:
 * - Track which passive abilities are active for each player
 * - Provide tick execution for toggleable and permanent passives
 * - Manage ability state across player sessions
 */
public class PassiveAbilityManager {
    // Track enabled toggleable abilities per player {playerId -> Set<abilityName>}
    private final Map<UUID, Set<String>> enabledToggleableAbilities;

    public PassiveAbilityManager() {
        this.enabledToggleableAbilities = new ConcurrentHashMap<>();
    }

    /**
     * Register a player's abilities when they join or advance sequence
     */
    public void registerPlayer(UUID playerId, List<Ability> abilities) {
        // Initialize toggleable abilities set if not exists
        enabledToggleableAbilities.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // Note: We don't auto-enable toggleable passives, player must enable them manually
    }

    /**
     * Unregister player when they leave
     */
    public void unregisterPlayer(UUID playerId) {
        enabledToggleableAbilities.remove(playerId);
    }

    /**
     * Toggle a toggleable passive ability on/off.
     * Returns the new state and calls appropriate lifecycle hooks.
     *
     * @param playerId Player UUID
     * @param ability The ability to toggle
     * @param context Context for calling lifecycle hooks
     * @return true if now enabled, false if now disabled
     */
    public boolean toggleAbility(UUID playerId, ToggleablePassiveAbility ability, IAbilityContext context) {
        Set<String> enabled = enabledToggleableAbilities.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        String abilityName = ability.getName();
        boolean wasEnabled = enabled.contains(abilityName);

        if (wasEnabled) {
            // Disable
            enabled.remove(abilityName);
            ability.onDisable(context);
            return false;
        } else {
            // Enable
            enabled.add(abilityName);
            ability.onEnable(context);
            return true;
        }
    }

    /**
     * Check if a toggleable ability is enabled for a player
     *
     * @param playerId Player UUID
     * @param abilityName Ability name
     * @return true if enabled, false otherwise
     */
    public boolean isToggleableEnabled(UUID playerId, String abilityName) {
        Set<String> enabled = enabledToggleableAbilities.get(playerId);
        return enabled != null && enabled.contains(abilityName);
    }

    /**
     * Get all toggleable passives that should tick for this player
     */
    public List<ToggleablePassiveAbility> getActiveToggleables(Beyonder beyonder) {
        Set<String> enabled = enabledToggleableAbilities.get(beyonder.getPlayerId());
        if (enabled == null || enabled.isEmpty()) {
            return Collections.emptyList();
        }

        return beyonder.getAbilities().stream()
                .filter(ability -> ability instanceof ToggleablePassiveAbility)
                .map(ability -> (ToggleablePassiveAbility) ability)
                .filter(ability -> enabled.contains(ability.getName()))
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
     * @param context Ability execution context
     */
    public void tickPlayer(Beyonder beyonder, IAbilityContext context) {
        if (beyonder == null) {
            return;
        }

        // Tick enabled toggleable passives
        List<ToggleablePassiveAbility> toggleables = getActiveToggleables(beyonder);
        for (ToggleablePassiveAbility ability : toggleables) {
            try {
                ability.tick(context);
            } catch (Exception e) {
                System.err.println("Error ticking toggleable ability " +
                        ability.getName() + " for player " + beyonder.getPlayerId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Tick permanent passives
        List<PermanentPassiveAbility> permanents = getPermanentPassives(beyonder);
        for (PermanentPassiveAbility ability : permanents) {
            try {
                ability.tick(context);
            } catch (Exception e) {
                System.err.println("Error ticking permanent ability " +
                        ability.getName() + " for player " + beyonder.getPlayerId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Activate all permanent passives for a player (called on join).
     * Calls onActivate lifecycle hook.
     *
     * @param beyonder Player's beyonder
     * @param context Ability execution context
     */
    public void activatePermanentPassives(Beyonder beyonder, IAbilityContext context) {
        List<PermanentPassiveAbility> permanents = getPermanentPassives(beyonder);
        for (PermanentPassiveAbility ability : permanents) {
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
     * Deactivate all permanent passives for a player (called on quit).
     * Calls onDeactivate lifecycle hook.
     *
     * @param beyonder Player's beyonder
     * @param context Ability execution context
     */
    public void deactivatePermanentPassives(Beyonder beyonder, IAbilityContext context) {
        List<PermanentPassiveAbility> permanents = getPermanentPassives(beyonder);
        for (PermanentPassiveAbility ability : permanents) {
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
     *
     * @param beyonder Player's beyonder
     * @param context Ability execution context
     */
    public void disableAllToggleables(Beyonder beyonder, IAbilityContext context) {
        UUID playerId = beyonder.getPlayerId();
        Set<String> enabled = enabledToggleableAbilities.get(playerId);
        if (enabled == null || enabled.isEmpty()) {
            return;
        }

        // Get all enabled toggleables
        List<ToggleablePassiveAbility> toDisable = beyonder.getAbilities().stream()
                .filter(ability -> ability instanceof ToggleablePassiveAbility)
                .map(ability -> (ToggleablePassiveAbility) ability)
                .filter(ability -> enabled.contains(ability.getName()))
                .toList();

        // Call onDisable for each
        for (ToggleablePassiveAbility ability : toDisable) {
            try {
                ability.onDisable(context);
            } catch (Exception e) {
                System.err.println("Error disabling toggleable ability " +
                        ability.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Clear enabled set
        enabled.clear();
    }

    /**
     * Cleanup all abilities for a player (called on quit or ability loss).
     * Calls all lifecycle hooks and cleans up ability state.
     *
     * @param beyonder Player's beyonder
     * @param context Ability execution context
     */
    public void cleanupPlayer(Beyonder beyonder, IAbilityContext context) {
        if (beyonder == null) {
            return;
        }

        // Disable all toggleables
        disableAllToggleables(beyonder, context);

        // Deactivate all permanents
        deactivatePermanentPassives(beyonder, context);

        // Call cleanUp on all abilities
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
        unregisterPlayer(beyonder.getPlayerId());
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
