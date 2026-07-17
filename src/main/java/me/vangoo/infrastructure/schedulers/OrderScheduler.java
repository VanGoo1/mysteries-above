package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.SecretOrderService;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Щохвилини будить SecretOrderService.tick() — респавн священиків, чистка простроченого intel. */
public class OrderScheduler {

    private final Plugin plugin;
    private final SecretOrderService secretOrderService;
    private BukkitTask task;

    public OrderScheduler(Plugin plugin, SecretOrderService secretOrderService) {
        this.plugin = plugin;
        this.secretOrderService = secretOrderService;
    }

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, secretOrderService::tick, 1200L, 1200L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
