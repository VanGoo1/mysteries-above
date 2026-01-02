package me.vangoo.infrastructure.schedulers;

import de.slikey.effectlib.EffectManager;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.*;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Infrastructure: Bukkit scheduler for passive abilities
 * <p>
 * Responsibilities:
 * - Schedule and manage tick execution for passive abilities
 * - Create ability contexts for each player
 * - Handle player join/quit lifecycle
 */
public class PassiveAbilityScheduler {
    private final MysteriesAbovePlugin plugin;
    private final PassiveAbilityManager passiveAbilityManager;
    private final BeyonderService beyonderService;
    private final AbilityContextFactory abilityContextFactory;


    private BukkitTask tickTask;
    private final Map<UUID, IAbilityContext> playerContexts;

    private static final long TICK_INTERVAL = 1L; // Every tick (20 times per second)

    public PassiveAbilityScheduler(
            MysteriesAbovePlugin plugin,
            PassiveAbilityManager passiveAbilityManager,
            BeyonderService beyonderService,
            AbilityContextFactory abilityContextFactory

    ) {
        this.plugin = plugin;
        this.passiveAbilityManager = passiveAbilityManager;
        this.beyonderService = beyonderService;
        this.abilityContextFactory = abilityContextFactory;

        this.playerContexts = new HashMap<>();
    }

    /**
     * Start the scheduler
     */
    public void start() {
        if (tickTask != null && !tickTask.isCancelled()) {
            return; // Already running
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tickAllPlayers,
                0L, // Start immediately
                TICK_INTERVAL
        );

        plugin.getLogger().info("PassiveAbilityScheduler started");
    }

    /**
     * Stop the scheduler
     */
    public void stop() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }

        // Cleanup all player contexts
        playerContexts.clear();

        plugin.getLogger().info("PassiveAbilityScheduler stopped");
    }

    /**
     * Called every tick by Bukkit scheduler
     */
    private void tickAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Beyonder beyonder = beyonderService.getBeyonder(playerId);

            if (beyonder == null) {
                continue;
            }

            // Get or create context for this player
            IAbilityContext context = getOrCreateContext(player);

            // Tick all passive abilities
            passiveAbilityManager.tickPlayer(beyonder, context);
        }
    }

    /**
     * Get or create ability context for a player
     */
    private IAbilityContext getOrCreateContext(Player player) {
        UUID playerId = player.getUniqueId();

        // Check cache first
        IAbilityContext cached = playerContexts.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Create new context
        IAbilityContext context = abilityContextFactory.createContext(player);

        playerContexts.put(playerId, context);
        return context;
    }


    /**
     * Register a player (called on join or when becoming beyonder)
     */
    public void registerPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Beyonder beyonder = beyonderService.getBeyonder(playerId);

        if (beyonder == null) {
            return;
        }

        // Register with passive ability manager
        passiveAbilityManager.registerPlayer(playerId, beyonder.getAbilities());
        passiveAbilityManager.activatePermanentPassives(beyonder, getOrCreateContext(player));

        plugin.getLogger().info("Registered player " + player.getName() + " for passive abilities");
    }

    /**
     * Unregister a player (called on quit)
     */
    public void unregisterPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Beyonder beyonder = beyonderService.getBeyonder(playerId);

        if (beyonder != null) {
            passiveAbilityManager.cleanupPlayer(beyonder, getOrCreateContext(player));
        } else {
            passiveAbilityManager.unregisterPlayer(playerId);
        }
        // Cleanup abilities
        passiveAbilityManager.cleanupPlayer(beyonder, getOrCreateContext(player));

        // Remove context
        playerContexts.remove(playerId);

        plugin.getLogger().info("Unregistered player " + player.getName() + " from passive abilities");
    }

    /**
     * Refresh a player's abilities (called on sequence advancement)
     */
    public void refreshPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Beyonder beyonder = beyonderService.getBeyonder(playerId);

        if (beyonder == null) {
            return;
        }

        // Get context
        IAbilityContext context = getOrCreateContext(player);

        // Deactivate old passives
        passiveAbilityManager.deactivatePermanentPassives(beyonder, context);
        passiveAbilityManager.disableAllToggleables(beyonder, context);

        // Re-register with new abilities
        passiveAbilityManager.registerPlayer(playerId, beyonder.getAbilities());

        // Activate new permanent passives
        passiveAbilityManager.activatePermanentPassives(beyonder, context);

        plugin.getLogger().info("Refreshed passive abilities for " + player.getName());
    }

    /**
     * Get statistics about the scheduler
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("isRunning", tickTask != null && !tickTask.isCancelled());
        stats.put("registeredPlayers", playerContexts.size());
        stats.putAll(passiveAbilityManager.getStatistics());
        return stats;
    }
}
