package me.vangoo.application.services;

import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TemporaryEventManager {
    private final Plugin plugin;
    private final Map<UUID, List<SubscriptionHandle>> subscriptions = new ConcurrentHashMap<>();

    public TemporaryEventManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public <T extends Event> void subscribe(
            UUID ownerId,
            Class<T> eventClass,
            Predicate<T> filter,
            Consumer<T> handler,
            int durationTicks
    ) {
        EventListener listener = new EventListener(eventClass, filter, handler);

        Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.NORMAL,
                (l, event) -> listener.handle(event),
                plugin
        );

        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> unsubscribe(ownerId, listener),
                durationTicks
        );

        subscriptions.computeIfAbsent(ownerId, k -> new ArrayList<>())
                .add(new SubscriptionHandle(listener, expiryTask));
    }

    public void unsubscribeAll(UUID ownerId) {
        List<SubscriptionHandle> handles = subscriptions.remove(ownerId);
        if (handles != null) {
            handles.forEach(h -> {
                HandlerList.unregisterAll(h.listener);
                h.expiryTask.cancel();
            });
        }
    }

    private void unsubscribe(UUID ownerId, EventListener listener) {
        List<SubscriptionHandle> handles = subscriptions.get(ownerId);
        if (handles != null) {
            handles.removeIf(h -> h.listener == listener);
            HandlerList.unregisterAll(listener);
        }
    }

    private record SubscriptionHandle(EventListener listener, BukkitTask expiryTask) {}

    private static class EventListener implements Listener {
        private final Class<? extends Event> eventClass;
        private final Predicate<Event> filter;
        private final Consumer<Event> handler;

        @SuppressWarnings("unchecked")
        EventListener(Class<?> eventClass, Predicate<?> filter, Consumer<?> handler) {
            this.eventClass = (Class<? extends Event>) eventClass;
            this.filter = (Predicate<Event>) filter;
            this.handler = (Consumer<Event>) handler;
        }

        void handle(Event event) {
            if (eventClass.isInstance(event) && filter.test(event)) {
                handler.accept(event);
            }
        }
    }
}