package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Mastery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * Infrastructure: Bukkit scheduler for automatic mastery regeneration
 *
 * Responsibilities:
 * - Automatically increase mastery by 1 every 30 minutes for all online beyonders
 * - Handle player notifications
 */
public class MasteryRegenerationScheduler {
    private static final Logger LOGGER = Logger.getLogger(MasteryRegenerationScheduler.class.getName());

    private final Plugin plugin;
    private final BeyonderService beyonderService;

    private BukkitTask regenerationTask;

    // 30 minutes = 30 * 60 * 20 ticks = 36000 ticks
    private static final long REGENERATION_INTERVAL_TICKS = 36000L;

    public MasteryRegenerationScheduler(Plugin plugin, BeyonderService beyonderService) {
        this.plugin = plugin;
        this.beyonderService = beyonderService;
    }

    /**
     * Start the scheduler
     */
    public void start() {
        if (regenerationTask != null && !regenerationTask.isCancelled()) {
            return; // Already running
        }

        regenerationTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::regenerateAllMastery,
                REGENERATION_INTERVAL_TICKS, // Initial delay
                REGENERATION_INTERVAL_TICKS  // Period
        );

        LOGGER.info("MasteryRegenerationScheduler started (30 min interval)");
    }

    /**
     * Stop the scheduler
     */
    public void stop() {
        if (regenerationTask != null && !regenerationTask.isCancelled()) {
            regenerationTask.cancel();
            regenerationTask = null;
        }

        LOGGER.info("MasteryRegenerationScheduler stopped");
    }

    /**
     * Called every 30 minutes by Bukkit scheduler
     */
    private void regenerateAllMastery() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

            if (beyonder == null) {
                continue;
            }

            // Don't regenerate if already at max
            if (beyonder.getMastery().canAdvance()) {
                continue;
            }

            // Increment mastery by 1
            Mastery oldMastery = beyonder.getMastery();
            Mastery newMastery = oldMastery.increment(1);
            beyonder.setMastery(newMastery);

            // Update and save
            beyonderService.updateBeyonder(beyonder);

            // Notify player
            player.sendMessage(ChatColor.GOLD + "✦ Засвоєння збільшено: " +
                    ChatColor.YELLOW + oldMastery.value() + "% " +
                    ChatColor.GRAY + "→ " +
                    ChatColor.GREEN + newMastery.value() + "%");

            // Check if can advance
            if (newMastery.canAdvance()) {
                player.sendMessage(ChatColor.GREEN + "✓ Ви можете перейти до наступної послідовності!");
            }
        }
    }
}