// src/main/java/me/vangoo/domain/events/AbilityDomainEvent.java
package me.vangoo.domain.events;

import java.util.UUID;

/**
 * Domain Events: Ability usage events
 *
 * Sealed interface для ability events
 */
public sealed interface AbilityDomainEvent {
    UUID casterId();
    String abilityName();
    long occurredAt();

    /**
     * Здібність була успішно використана
     */
    record AbilityUsed(
            UUID casterId,
            String abilityName,
            String pathwayName,
            int sequenceLevel,
            boolean isOffPathway,
            long occurredAt
    ) implements AbilityDomainEvent {
        public AbilityUsed(UUID casterId, String abilityName, String pathwayName,
                           int sequenceLevel, boolean isOffPathway) {
            this(casterId, abilityName, pathwayName, sequenceLevel, isOffPathway,
                    System.currentTimeMillis());
        }
    }
}