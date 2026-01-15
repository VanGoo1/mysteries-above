package me.vangoo.domain.abilities.context;

import org.bukkit.scheduler.BukkitTask;

public interface ISchedulingContext {
    BukkitTask scheduleDelayed(Runnable task, long delayTicks);

    BukkitTask scheduleRepeating(Runnable task, long delayTicks, long periodTicks);

    void runAsync(Runnable task);
}
