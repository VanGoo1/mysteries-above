package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.GatheringService;
import me.vangoo.domain.market.GatheringPhase;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/** Щохвилини перевіряє, чи настав час оголосити Збори (час персистується в снепшоті). */
public class GatheringScheduler {

    private static final Logger LOGGER = Logger.getLogger(GatheringScheduler.class.getName());
    private static final long CHECK_INTERVAL_TICKS = 60L * 20L;

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private BukkitTask task;

    public GatheringScheduler(Plugin plugin, GatheringService gatheringService) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
    }

    public void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        LOGGER.info("GatheringScheduler started (1 min interval)");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        LOGGER.info("GatheringScheduler stopped");
    }

    private void check() {
        if (gatheringService.phase() == GatheringPhase.IDLE
                && System.currentTimeMillis() >= gatheringService.nextGatheringMillis()) {
            gatheringService.announce();
        }
    }
}
