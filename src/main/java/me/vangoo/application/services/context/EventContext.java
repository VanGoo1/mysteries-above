package me.vangoo.application.services.context;

import me.vangoo.application.services.CoreProtectHandler;
import me.vangoo.application.services.DomainEventPublisher;
import me.vangoo.domain.abilities.context.IEventContext;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.RecordedEvent;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class EventContext implements IEventContext {

    private final DomainEventPublisher eventPublisher;

    public EventContext(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publishAbilityUsedEvent(ActiveAbility activeAbility, Beyonder caster) {
        boolean isOffPathway = caster.getOffPathwayActiveAbilities()
                .stream()
                .anyMatch(a -> a.getIdentity().equals(activeAbility.getIdentity()));

        eventPublisher.publishAbility(
                new AbilityDomainEvent.AbilityUsed(
                        caster.getPlayerId(),
                        activeAbility.getName(),
                        caster.getPathway().getName(),
                        caster.getSequenceLevel(),
                        isOffPathway
                )
        );
    }

    @Override
    public void subscribeToAbilityEvents(Consumer<AbilityDomainEvent> handler) {
        eventPublisher.subscribeToAbility(handler);
    }

    @Override
    public Optional<AbilityDomainEvent> getLastAbilityEvent(UUID casterId, int maxAgeSeconds) {
        return eventPublisher.getLastAbilityEvent(casterId, maxAgeSeconds);
    }

    @Override
    public List<AbilityDomainEvent> getAbilityEventHistory(UUID casterId, int maxAgeSeconds) {
        return eventPublisher.getAbilityEventHistory(casterId, maxAgeSeconds);
    }

    @Override
    public List<RecordedEvent> getPastEvents(Location location, int radius, int timeSeconds) {
        return CoreProtectHandler.lookupEvents(location, radius, timeSeconds);
    }
}
