package me.vangoo.domain.events;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.RampageState;

import java.util.UUID;

/**
 * Domain Events: Pure domain events without infrastructure dependencies
 *
 * These are simple data carriers that describe what happened in the domain
 */
public sealed interface RampageDomainEvent {
    UUID playerId();
    long occurredAt();

    /**
     * Rampage transformation started
     */
    record RampageStarted(
            UUID playerId,
            int sequenceLevel,
            int sanityLoss,
            int durationSeconds,
            long occurredAt
    ) implements RampageDomainEvent {
        public RampageStarted(UUID playerId, Beyonder beyonder, int durationSeconds) {
            this(
                    playerId,
                    beyonder.getSequenceLevel(),
                    beyonder.getSanityLossScale(),
                    durationSeconds,
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * Rampage phase changed (building → critical → transforming)
     */
    record PhaseChanged(
            UUID playerId,
            RampageState.RampagePhase oldPhase,
            RampageState.RampagePhase newPhase,
            int remainingSeconds,
            long occurredAt
    ) implements RampageDomainEvent {
        public PhaseChanged(UUID playerId, RampageState oldState, RampageState newState) {
            this(
                    playerId,
                    oldState.phase(),
                    newState.phase(),
                    newState.getRemainingSeconds(),
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * Transformation completed - point of no return
     */
    record TransformationCompleted(
            UUID playerId,
            long occurredAt
    ) implements RampageDomainEvent {
        public TransformationCompleted(UUID playerId) {
            this(playerId, System.currentTimeMillis());
        }
    }

    /**
     * Player rescued from rampage
     */
    record RampageRescued(
            UUID playerId,
            UUID rescuerId,
            int remainingSeconds,
            long occurredAt
    ) implements RampageDomainEvent {
        public RampageRescued(UUID playerId, UUID rescuerId, RampageState state) {
            this(
                    playerId,
                    rescuerId,
                    state.getRemainingSeconds(),
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * Rampage cancelled (admin action)
     */
    record RampageCancelled(
            UUID playerId,
            long occurredAt
    ) implements RampageDomainEvent {
        public RampageCancelled(UUID playerId) {
            this(playerId, System.currentTimeMillis());
        }
    }
}