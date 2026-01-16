package me.vangoo.domain.abilities.context;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.RecordedEvent;
import org.bukkit.Location;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface IEventContext {

    /**
     * Broadcasts to the system that an ability has been cast.
     * <p>
     * This notifies all internal subscribers and records the action in history
     * (essential for "Record" or "Replay" mechanics).
     *
     * @param activeAbility The ability being used.
     * @param caster        The player casting the ability.
     */
    void publishAbilityUsedEvent(ActiveAbility activeAbility, Beyonder caster);

    /**
     * Subscribes to the internal stream of magic events.
     * <p>
     * Use this to listen for high-level system events (e.g., "Another player cast Fireball")
     * rather than low-level physical events.
     *
     * @param handler The logic to execute when an ability event occurs.
     */
    void subscribeToAbilityEvents(Consumer<AbilityDomainEvent> handler);


    /**
     * Temporarily subscribes to internal magic events with a timeout.
     * <p>
     * Useful for combo mechanics or reactions (e.g., "Counter-spell if cast within 2 seconds").
     *
     * @param handler       A function that handles the event. Return {@code true} to unsubscribe immediately.
     * @param durationTicks How long (in ticks) to listen before automatically unsubscribing.
     */
    void subscribeToAbilityEvents(
            Function<AbilityDomainEvent, Boolean> handler,
            int durationTicks
    );

    /**
     * Temporarily listens to native Minecraft (Bukkit) events.
     * <p>
     * Use this for physical interactions (e.g., taking damage, clicking a block, moving)
     * that are necessary for an ability's logic.
     *
     * @param playerId      The UUID of the player (owner of the listener).
     * @param eventClass    The Bukkit event class to listen for (e.g., EntityDamageEvent.class).
     * @param filter        A condition to check before executing the handler.
     * @param handler       The logic to execute when the event matches.
     * @param durationTicks The lifespan of this listener in ticks.
     */
    <T extends Event> void subscribeToTemporaryEvent(UUID playerId, Class<T> eventClass, Predicate<T> filter, Consumer<T> handler, int durationTicks);

    Optional<AbilityDomainEvent> getLastAbilityEvent(UUID casterId, int maxAgeSeconds);

    List<AbilityDomainEvent> getAbilityEventHistory(UUID casterId, int maxAgeSeconds);

    List<RecordedEvent> getPastEvents(Location location, int radius, int timeSeconds);
}
