package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.schedulers.PassiveAbilityScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for managing passive ability lifecycle
 *
 * Responsibilities:
 * - Register players when they join
 * - Unregister players when they quit
 * - Ensure proper cleanup of passive abilities
 */
public class PassiveAbilityLifecycleListener implements Listener {
    private final PassiveAbilityScheduler scheduler;

    public PassiveAbilityLifecycleListener(PassiveAbilityScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Register player for passive abilities when they join
     * Priority: MONITOR to run after other join handlers (like BeyonderPlayerListener)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Register player with scheduler
        // This will also register with PassiveAbilityManager
        scheduler.registerPlayer(player);
    }

    /**
     * Unregister player when they quit
     * Priority: MONITOR to ensure cleanup happens last
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Unregister player from scheduler
        // This will also cleanup abilities and remove from PassiveAbilityManager
        scheduler.unregisterPlayer(player);
    }
}
