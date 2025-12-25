package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.rampager.RampageManager;
import me.vangoo.domain.valueobjects.RampageState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Infrastructure: Bukkit scheduler for rampage system
 * <p>
 * Responsibilities:
 * - Update rampage states every tick
 * - Display visual feedback to players
 * - Play sounds and particles
 */
public class RampageScheduler {
    private final Logger LOGGER;

    private final Plugin plugin;
    private final RampageManager rampageManager;

    private BukkitTask updateTask;

    private static final long TICK_INTERVAL = 1L; // Every tick

    public RampageScheduler(Plugin plugin, RampageManager rampageManager) {
        this.plugin = plugin;
        this.rampageManager = rampageManager;
        this.LOGGER = plugin.getLogger();
    }

    /**
     * Start the scheduler
     */
    public void start() {
        if (updateTask != null && !updateTask.isCancelled()) {
            return; // Already running
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tickAllRampages,
                0L, // Start immediately
                TICK_INTERVAL
        );

        LOGGER.info("RampageScheduler started");
    }

    /**
     * Stop the scheduler
     */
    public void stop() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }

        LOGGER.info("RampageScheduler stopped");
    }

    /**
     * Called every tick by Bukkit scheduler
     */
    private void tickAllRampages() {
        Set<UUID> playersInRampage = rampageManager.getPlayersInRampage();

        for (UUID playerId : playersInRampage) {
            tickRampage(playerId);
        }
    }

    /**
     * Update rampage for single player
     */
    private void tickRampage(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            rampageManager.onPlayerQuit(playerId);
            return;
        }

        // Update state
        RampageState state = rampageManager.updateRampage(playerId);

        if (state == null) {
            return; // No longer in rampage
        }

        // Show visual feedback every second
        if (state.getElapsedSeconds() > 0 &&
                System.currentTimeMillis() % 1000 < 50) { // ~every second
            showRampageProgress(player, state);
        }

        // Continuous visual effects
        showContinuousEffects(player, state);
    }

    /**
     * Show rampage progress to player
     */
    private void showRampageProgress(Player player, RampageState state) {
        int remaining = state.getRemainingSeconds();

        // Action bar countdown
        String message = formatCountdownMessage(remaining, state);
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(message)
        );

        // Sound warnings at specific intervals
        if (remaining == 15 || remaining == 10 || remaining == 5 || remaining <= 3) {
            playWarningSound(player, remaining);
        }
    }

    /**
     * Format countdown message with color coding
     */
    private String formatCountdownMessage(int remaining, RampageState state) {
        ChatColor color;
        String urgency;

        if (remaining <= 3) {
            color = ChatColor.DARK_RED;
            urgency = "⚠⚠⚠ ";
        } else if (remaining <= 10) {
            color = ChatColor.RED;
            urgency = "⚠⚠ ";
        } else if (remaining <= 15) {
            color = ChatColor.GOLD;
            urgency = "⚠ ";
        } else {
            color = ChatColor.YELLOW;
            urgency = "";
        }

        return urgency + color + ChatColor.BOLD + "ТРАНСФОРМАЦІЯ: " +
                remaining + "с " + ChatColor.RESET + urgency;
    }

    /**
     * Play warning sound based on urgency
     */
    private void playWarningSound(Player player, int remaining) {
        if (remaining <= 3) {
            // Final countdown - loud
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 2.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.0f);
        } else if (remaining <= 10) {
            // Critical phase
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 0.7f, 0.8f);
        } else {
            // Warning phase
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        }
    }

    /**
     * Show continuous particle effects
     */
    private void showContinuousEffects(Player player, RampageState state) {
        // Show effects every 10 ticks (0.5 seconds)
        if (System.currentTimeMillis() % 500 > 50) {
            return;
        }

        Particle particle;
        int count;
        double spread;

        if (state.isCritical()) {
            // Critical phase - intense effects
            particle = Particle.SOUL_FIRE_FLAME;
            count = 5;
            spread = 0.5;
        } else {
            // Building up - moderate effects
            particle = Particle.SMOKE;
            count = 3;
            spread = 0.3;
        }

        player.getWorld().spawnParticle(
                particle,
                player.getLocation().add(0, 1, 0),
                count,
                spread, spread, spread,
                0.01
        );

        // Add dark particles for critical phase
        if (state.isCritical()) {
            player.getWorld().spawnParticle(
                    Particle.SQUID_INK,
                    player.getLocation().add(0, 1, 0),
                    2,
                    0.3, 0.5, 0.3,
                    0
            );
        }
    }

    /**
     * Check if scheduler is running
     */
    public boolean isRunning() {
        return updateTask != null && !updateTask.isCancelled();
    }
}