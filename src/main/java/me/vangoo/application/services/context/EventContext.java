package me.vangoo.application.services.context;

import me.vangoo.application.services.CoreProtectHandler;
import me.vangoo.application.services.DomainEventPublisher;
import me.vangoo.application.services.TemporaryEventManager;
import me.vangoo.domain.abilities.context.IEventContext;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.RecordedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class EventContext implements IEventContext {

    private final DomainEventPublisher eventPublisher;
    private final Plugin plugin;
    private final TemporaryEventManager temporaryEventManager;

    public EventContext(DomainEventPublisher eventPublisher, Plugin plugin, TemporaryEventManager temporaryEventManager) {
        this.eventPublisher = eventPublisher;
        this.plugin = plugin;
        this.temporaryEventManager = temporaryEventManager;
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
    public void subscribeToAbilityEvents(
            Function<AbilityDomainEvent, Boolean> handler,
            int durationTicks
    ) {
        // Wrapper для автоматичного відписування
        Consumer<AbilityDomainEvent> wrapper = new Consumer<>() {
            @Override
            public void accept(AbilityDomainEvent event) {
                boolean shouldUnsubscribe = handler.apply(event);
                if (shouldUnsubscribe) {
                    eventPublisher.unsubscribeFromAbility(this);
                }
            }
        };
        eventPublisher.subscribeToAbility(wrapper);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            eventPublisher.unsubscribeFromAbility(wrapper);
        }, durationTicks);
    }

    @Override
    public void unsubscribeAll(UUID subscriptionId) {
        temporaryEventManager.unsubscribeAll(subscriptionId);
    }
    @Override
    public <T extends Event> void subscribeToTemporaryEvent(UUID playerId,Class<T> eventClass, Predicate<T> filter, Consumer<T> handler, int durationTicks) {
        temporaryEventManager.subscribe(playerId, eventClass, filter, handler, durationTicks);
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
