package me.vangoo.presentation.listeners;

import me.vangoo.application.services.ChurchDuelService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Події дуелі: заморозка під час доповіді, скасування смертельного удару (поразка),
 * смерть опонента (перемога), безпечне завершення при виході/вході.
 */
public class DuelListener implements Listener {

    private final ChurchDuelService duelService;

    public DuelListener(ChurchDuelService duelService) {
        this.duelService = duelService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!duelService.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!duelService.hasActiveDuel(player.getUniqueId())) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            duelService.onPlayerLost(player);
        }
    }

    @EventHandler
    public void onOpponentDeath(EntityDeathEvent event) {
        duelService.opponentDied(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        duelService.abandon(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        duelService.handleStrandedOnJoin(event.getPlayer());
    }
}
