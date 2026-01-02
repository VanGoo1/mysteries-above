package me.vangoo.application.services;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.RampageDomainEvent;
import me.vangoo.domain.valueobjects.RampageState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Application Service: Manages rampage transformation process
 * <p>
 * Responsibilities:
 * - Track rampage states
 * - Coordinate transformation timing
 * - Publish domain events (NOT handle them)
 * - Pure business logic - NO visual effects
 */
public class RampageManager {
    private static final Logger LOGGER = Logger.getLogger(RampageManager.class.getName());
    private static final int DEFAULT_DURATION_SECONDS = 20;

    private final Map<UUID, RampageState> rampageStates;
    private final DomainEventPublisher eventPublisher;

    public RampageManager(DomainEventPublisher eventPublisher) {
        this.rampageStates = new ConcurrentHashMap<>();
        this.eventPublisher = eventPublisher;
    }

    /**
     * Start rampage transformation for player
     */
    public boolean startRampage(UUID playerId, Beyonder beyonder) {
        return startRampage(playerId, beyonder, DEFAULT_DURATION_SECONDS);
    }

    /**
     * Start rampage with custom duration
     */
    public boolean startRampage(UUID playerId, Beyonder beyonder, int durationSeconds) {
        if (isInRampage(playerId)) {
            return false;
        }

        RampageState state = RampageState.start(durationSeconds);
        rampageStates.put(playerId, state);

        // Publish domain event
        RampageDomainEvent event = new RampageDomainEvent.RampageStarted(playerId, beyonder.getSequenceLevel(), beyonder.getSanityLoss().scale(), durationSeconds, System.currentTimeMillis());
        eventPublisher.publishRampage(event);


        LOGGER.info("Started rampage for player " + playerId + " (duration: " + durationSeconds + "s)");
        return true;
    }

    /**
     * Attempt to rescue player from rampage
     */
    public boolean rescueFromRampage(UUID playerId, UUID rescuerId) {
        RampageState state = rampageStates.get(playerId);

        if (state == null || !state.isActive()) {
            return false;
        }

        if (state.shouldTransform()) {
            return false; // Too late
        }

        // Cancel rampage
        rampageStates.remove(playerId);

        // Publish domain event
        RampageDomainEvent event = new RampageDomainEvent.RampageRescued(playerId, rescuerId, state.getRemainingSeconds(), System.currentTimeMillis());

        eventPublisher.publishRampage(event);

        LOGGER.info("Player " + playerId + " rescued from rampage by " + rescuerId);
        return true;
    }

    /**
     * Update rampage state (called each tick)
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
            RampageDomainEvent event = new RampageDomainEvent.PhaseChanged(playerId, state, updatedState);

            eventPublisher.publishRampage(event);
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
     */
    private void completeTransformation(UUID playerId) {
        rampageStates.remove(playerId);

        // Publish domain event
        RampageDomainEvent event = new RampageDomainEvent.TransformationCompleted(playerId, System.currentTimeMillis());
        eventPublisher.publishRampage(event);

        LOGGER.info("Player " + playerId + " completed rampage transformation");
    }

    /**
     * Check if player is in rampage
     */
    public boolean isInRampage(UUID playerId) {
        RampageState state = rampageStates.get(playerId);
        return state != null && state.isActive();
    }

    /**
     * Get rampage state for player
     */
    public Optional<RampageState> getRampageState(UUID playerId) {
        return Optional.ofNullable(rampageStates.get(playerId));
    }

    /**
     * Get all players currently in rampage
     */
    public Set<UUID> getPlayersInRampage() {
        return new HashSet<>(rampageStates.keySet());
    }

    /**
     * Force cancel rampage (admin command)
     */
    public boolean cancelRampage(UUID playerId) {
        RampageState state = rampageStates.remove(playerId);

        if (state != null) {
            RampageDomainEvent event = new RampageDomainEvent.RampageCancelled(playerId, System.currentTimeMillis());
            eventPublisher.publishRampage(event);
            LOGGER.info("Rampage cancelled for player " + playerId);
            return true;
        }

        return false;
    }

    /**
     * Clear rampage on player quit
     */
    public void onPlayerQuit(UUID playerId) {
        rampageStates.remove(playerId);
    }
}