package me.vangoo.application.services.context;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.ISchedulingContext;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class SchedulingContext implements ISchedulingContext {

    private final MysteriesAbovePlugin plugin;

    public SchedulingContext(MysteriesAbovePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public BukkitTask scheduleDelayed(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public BukkitTask scheduleRepeating(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
}
