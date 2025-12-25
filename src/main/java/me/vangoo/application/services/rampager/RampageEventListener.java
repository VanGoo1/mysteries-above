package me.vangoo.application.services.rampager;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.RampageState;

import java.util.UUID;

/**
 * Interface for listening to rampage events
 */
public interface RampageEventListener {
    default void onRampageStarted(UUID playerId, Beyonder beyonder, RampageState state) {
    }

    default void onPhaseChanged(UUID playerId, RampageState oldState, RampageState newState) {
    }

    default void onTransformationComplete(UUID playerId, RampageState state) {
    }

    default void onRampageRescued(UUID playerId, UUID rescuerId, RampageState state) {
    }

    default void onRampageCancelled(UUID playerId, RampageState state) {
    }
}