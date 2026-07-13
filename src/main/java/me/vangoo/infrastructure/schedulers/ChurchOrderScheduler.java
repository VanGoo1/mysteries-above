package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.ChurchService;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Щохвилини будить ChurchService.tickOrders() — сповіщення про готові зілля. */
public class ChurchOrderScheduler {

    private final Plugin plugin;
    private final ChurchService churchService;
    private BukkitTask task;

    public ChurchOrderScheduler(Plugin plugin, ChurchService churchService) {
        this.plugin = plugin;
        this.churchService = churchService;
    }

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, churchService::tickOrders, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
