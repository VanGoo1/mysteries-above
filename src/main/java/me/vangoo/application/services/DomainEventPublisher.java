package me.vangoo.application.services;

import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.events.RampageDomainEvent;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Application Service: Universal event publisher з історією ability events
 *
 * ВАЖЛИВО: Зберігає останні 50 ability events для можливості "запису минулого"
 */
public class DomainEventPublisher {
    private static final Logger LOGGER = Logger.getLogger(DomainEventPublisher.class.getName());

    // History configuration
    private static final int MAX_ABILITY_HISTORY = 50; // Останні 50 подій
    private static final long EVENT_EXPIRY_MS = 30_000; // 30 секунд

    private final List<Consumer<RampageDomainEvent>> rampageSubscribers = new ArrayList<>();
    private final List<Consumer<AbilityDomainEvent>> abilitySubscribers = new ArrayList<>();

    // History buffer для ability events (для Record ability)
    private final Deque<TimestampedEvent> abilityHistory = new ConcurrentLinkedDeque<>();

    // ==========================================
    // RAMPAGE EVENTS (без змін)
    // ==========================================

    public void subscribeToRampage(Consumer<RampageDomainEvent> subscriber) {
        rampageSubscribers.add(subscriber);
    }

    public void unsubscribeFromRampage(Consumer<RampageDomainEvent> subscriber) {
        rampageSubscribers.remove(subscriber);
    }

    public void publishRampage(RampageDomainEvent event) {
        // Використовуємо копію, щоб уникнути ConcurrentModificationException
        List<Consumer<RampageDomainEvent>> subscribersCopy = new ArrayList<>(rampageSubscribers);
        for (Consumer<RampageDomainEvent> subscriber : subscribersCopy) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                LOGGER.severe("Error handling rampage event: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ==========================================
    // ABILITY EVENTS (з історією)
    // ==========================================

    public void subscribeToAbility(Consumer<AbilityDomainEvent> subscriber) {
        abilitySubscribers.add(subscriber);
    }

    public void unsubscribeFromAbility(Consumer<AbilityDomainEvent> subscriber) {
        abilitySubscribers.remove(subscriber);
    }

    public void publishAbility(AbilityDomainEvent event) {
        // Додати до історії
        addToHistory(event);

        // Розіслати підписникам (використовуємо копію, щоб уникнути ConcurrentModificationException)
        // Це потрібно, бо підписники можуть відписатися під час обробки події
        List<Consumer<AbilityDomainEvent>> subscribersCopy = new ArrayList<>(abilitySubscribers);
        for (Consumer<AbilityDomainEvent> subscriber : subscribersCopy) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                LOGGER.severe("Error handling ability event: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Отримати останню ability event від конкретного гравця
     *
     * @param casterId UUID гравця
     * @param maxAgeSeconds Максимальний вік події в секундах
     * @return Optional з подією або empty якщо не знайдено
     */
    public Optional<AbilityDomainEvent> getLastAbilityEvent(UUID casterId, int maxAgeSeconds) {
        cleanupHistory(); // Прибрати застарілі події

        long cutoffTime = System.currentTimeMillis() - (maxAgeSeconds * 1000L);

        // Шукаємо з кінця (найновіші події)
        for (TimestampedEvent te : abilityHistory) {
            if (te.timestamp < cutoffTime) {
                break; // Всі старіші події вже застарілі
            }

            if (te.event.casterId().equals(casterId)) {
                return Optional.of(te.event);
            }
        }

        return Optional.empty();
    }

    /**
     * Отримати всі ability events від гравця за останній період
     *
     * @param casterId UUID гравця
     * @param maxAgeSeconds Максимальний вік події
     * @return Список подій (від найновішої до найстарішої)
     */
    public List<AbilityDomainEvent> getAbilityEventHistory(UUID casterId, int maxAgeSeconds) {
        cleanupHistory();

        long cutoffTime = System.currentTimeMillis() - (maxAgeSeconds * 1000L);
        List<AbilityDomainEvent> result = new ArrayList<>();

        for (TimestampedEvent te : abilityHistory) {
            if (te.timestamp < cutoffTime) {
                break;
            }

            if (te.event.casterId().equals(casterId)) {
                result.add(te.event);
            }
        }

        return result;
    }

    // ==========================================
    // HISTORY MANAGEMENT
    // ==========================================

    private void addToHistory(AbilityDomainEvent event) {
        // Додати на початок (найновіша подія)
        abilityHistory.addFirst(new TimestampedEvent(event, System.currentTimeMillis()));

        // Обмежити розмір
        while (abilityHistory.size() > MAX_ABILITY_HISTORY) {
            abilityHistory.removeLast();
        }
    }

    private void cleanupHistory() {
        long expiryTime = System.currentTimeMillis() - EVENT_EXPIRY_MS;

        // Видалити застарілі події з кінця
        while (!abilityHistory.isEmpty()) {
            TimestampedEvent last = abilityHistory.peekLast();
            if (last != null && last.timestamp < expiryTime) {
                abilityHistory.removeLast();
            } else {
                break;
            }
        }
    }

    /**
     * Очистити всю історію (для тестування)
     */
    public void clearHistory() {
        abilityHistory.clear();
    }

    // ==========================================
    // GENERAL
    // ==========================================

    public void clear() {
        rampageSubscribers.clear();
        abilitySubscribers.clear();
        abilityHistory.clear();
    }

    public int getSubscriberCount() {
        return rampageSubscribers.size() + abilitySubscribers.size();
    }

    public int getHistorySize() {
        cleanupHistory();
        return abilityHistory.size();
    }

    // ==========================================
    // HELPER CLASSES
    // ==========================================

    private record TimestampedEvent(AbilityDomainEvent event, long timestamp) {}
}