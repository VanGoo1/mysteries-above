package me.vangoo.application.services;

import me.vangoo.domain.events.RampageDomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Application Service: Simple event publisher
 *
 * Responsibilities:
 * - Publish domain events to subscribers
 * - Manage subscriptions
 */
public class DomainEventPublisher {
    private static final Logger LOGGER = Logger.getLogger(DomainEventPublisher.class.getName());

    private final List<Consumer<RampageDomainEvent>> subscribers = new ArrayList<>();

    /**
     * Subscribe to domain events
     *
     * @param subscriber Callback that receives events
     */
    public void subscribe(Consumer<RampageDomainEvent> subscriber) {
        subscribers.add(subscriber);
    }

    /**
     * Unsubscribe from domain events
     *
     * @param subscriber Callback to remove
     */
    public void unsubscribe(Consumer<RampageDomainEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Publish event to all subscribers
     *
     * @param event Domain event
     */
    public void publish(RampageDomainEvent event) {
        for (Consumer<RampageDomainEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                LOGGER.severe("Error handling domain event " + event.getClass().getSimpleName() +
                        ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Clear all subscribers
     */
    public void clear() {
        subscribers.clear();
    }

    /**
     * Get subscriber count
     */
    public int subscriberCount() {
        return subscribers.size();
    }
}