package me.vangoo.application.services.rampager;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.RampageState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Application Service: Manages rampage transformation process
 * <p>
 * Responsibilities:
 * - Track players in rampage state
 * - Coordinate transformation timing
 * - Allow rescue attempts
 * - Notify effects handler
 */
public class RampageManager {
    private final Logger LOGGER;

    // Default transformation duration
    private static final int DEFAULT_DURATION_SECONDS = 20;

    // Track rampage states for each player
    private final Map<UUID, RampageState> rampageStates;

    // Listeners for rampage events
    private final List<RampageEventListener> listeners;

    public RampageManager(Logger logger) {
        this.rampageStates = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
        this.LOGGER = logger;
    }

    /**
     * Start rampage transformation for player
     *
     * @param playerId Player UUID
     * @param beyonder Player's beyonder
     * @return true if rampage started, false if already in rampage
     */
    public boolean startRampage(UUID playerId, Beyonder beyonder) {
        return startRampage(playerId, beyonder, DEFAULT_DURATION_SECONDS);
    }

    /**
     * Start rampage with custom duration
     *
     * @param playerId        Player UUID
     * @param beyonder        Player's beyonder
     * @param durationSeconds Duration before transformation
     * @return true if rampage started
     */
    public boolean startRampage(UUID playerId, Beyonder beyonder, int durationSeconds) {
        // Check if already in rampage
        if (isInRampage(playerId)) {
            return false;
        }

        // Create rampage state
        RampageState state = RampageState.start(durationSeconds);
        rampageStates.put(playerId, state);

        // Notify listeners
        notifyRampageStarted(playerId, beyonder, state);

        LOGGER.info("Started rampage for player " + playerId + " (duration: " + durationSeconds + "s)");
        return true;
    }

    /**
     * Attempt to rescue player from rampage
     *
     * @param playerId  Player to rescue
     * @param rescuerId Player who rescues (can be same)
     * @return true if rescued successfully
     */
    public boolean rescueFromRampage(UUID playerId, UUID rescuerId) {
        RampageState state = rampageStates.get(playerId);

        if (state == null || !state.isActive()) {
            return false; // Not in rampage
        }

        if (state.shouldTransform()) {
            return false; // Too late, transformation started
        }

        // Cancel rampage
        rampageStates.remove(playerId);

        // Notify listeners
        notifyRampageRescued(playerId, rescuerId, state);

        LOGGER.info("Player " + playerId + " rescued from rampage by " + rescuerId);
        return true;
    }

    /**
     * Update rampage state for player (called each tick)
     *
     * @param playerId Player UUID
     * @return Updated state, or null if not in rampage
     */
    public RampageState updateRampage(UUID playerId) {
        RampageState state = rampageStates.get(playerId);

        if (state == null || !state.isActive()) {
            return null;
        }

        // Update phase
        RampageState updatedState = state.updatePhase();

        // Check for phase changes
        if (updatedState.phase() != state.phase()) {
            notifyPhaseChanged(playerId, state, updatedState);
        }

        // Update stored state
        rampageStates.put(playerId, updatedState);

        // Check if should transform
        if (updatedState.shouldTransform()) {
            completeTransformation(playerId);
        }

        return updatedState;
    }

    /**
     * Complete transformation (point of no return)
     *
     * @param playerId Player UUID
     */
    private void completeTransformation(UUID playerId) {
        RampageState state = rampageStates.remove(playerId);

        if (state != null) {
            notifyTransformationComplete(playerId, state);
            LOGGER.info("Player " + playerId + " completed rampage transformation");
        }
    }

    /**
     * Check if player is in rampage
     *
     * @param playerId Player UUID
     * @return true if in rampage
     */
    public boolean isInRampage(UUID playerId) {
        RampageState state = rampageStates.get(playerId);
        return state != null && state.isActive();
    }

    /**
     * Get rampage state for player
     *
     * @param playerId Player UUID
     * @return Optional with state if in rampage
     */
    public Optional<RampageState> getRampageState(UUID playerId) {
        return Optional.ofNullable(rampageStates.get(playerId));
    }

    /**
     * Get all players currently in rampage
     *
     * @return Set of player UUIDs
     */
    public Set<UUID> getPlayersInRampage() {
        return new HashSet<>(rampageStates.keySet());
    }

    /**
     * Force cancel rampage (admin command)
     *
     * @param playerId Player UUID
     * @return true if cancelled
     */
    public boolean cancelRampage(UUID playerId) {
        RampageState state = rampageStates.remove(playerId);

        if (state != null) {
            notifyRampageCancelled(playerId, state);
            LOGGER.info("Rampage cancelled for player " + playerId);
            return true;
        }

        return false;
    }

    /**
     * Clear rampage on player quit
     *
     * @param playerId Player UUID
     */
    public void onPlayerQuit(UUID playerId) {
        rampageStates.remove(playerId);
    }

    // ==========================================
    // EVENT LISTENER SYSTEM
    // ==========================================

    public void addListener(RampageEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RampageEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyRampageStarted(UUID playerId, Beyonder beyonder, RampageState state) {
        for (RampageEventListener listener : listeners) {
            try {
                listener.onRampageStarted(playerId, beyonder, state);
            } catch (Exception e) {
                LOGGER.severe("Error in rampage listener: " + e.getMessage());
            }
        }
    }

    private void notifyPhaseChanged(UUID playerId, RampageState oldState, RampageState newState) {
        for (RampageEventListener listener : listeners) {
            try {
                listener.onPhaseChanged(playerId, oldState, newState);
            } catch (Exception e) {
                LOGGER.severe("Error in rampage listener: " + e.getMessage());
            }
        }
    }

    private void notifyTransformationComplete(UUID playerId, RampageState state) {
        for (RampageEventListener listener : listeners) {
            try {
                listener.onTransformationComplete(playerId, state);
            } catch (Exception e) {
                LOGGER.severe("Error in rampage listener: " + e.getMessage());
            }
        }
    }

    private void notifyRampageRescued(UUID playerId, UUID rescuerId, RampageState state) {
        for (RampageEventListener listener : listeners) {
            try {
                listener.onRampageRescued(playerId, rescuerId, state);
            } catch (Exception e) {
                LOGGER.severe("Error in rampage listener: " + e.getMessage());
            }
        }
    }

    private void notifyRampageCancelled(UUID playerId, RampageState state) {
        for (RampageEventListener listener : listeners) {
            try {
                listener.onRampageCancelled(playerId, state);
            } catch (Exception e) {
                LOGGER.severe("Error in rampage listener: " + e.getMessage());
            }
        }
    }
}