package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;

public class BeyonderSleepListener implements Listener {
    private final BeyonderService beyonderService;

    public BeyonderSleepListener(BeyonderService beyonderService) {
        this.beyonderService = beyonderService;
    }

    @EventHandler
    public void onPlayerWakeUp(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        // Check if player actually slept (not just got out of bed)
        // This requires checking if it's day now (sleep completed)
        if (!isDay(player)) {
            return; // Didn't complete sleep
        }

        // Apply rest recovery
        beyonder.restoreAfterSleep();
        beyonderService.updateBeyonder(beyonder);

        // Show recovery effects
        showRecoveryEffects(player);
    }

    /**
     * Check if it's daytime (sleep completed)
     */
    private boolean isDay(Player player) {
        long time = player.getWorld().getTime();
        // Day is 0-12000, night is 12000-24000
        return time >= 0 && time < 13000;
    }

    /**
     * Show visual and audio effects for recovery
     */
    private void showRecoveryEffects(Player player) {
        // Particle effects
        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1.5, 0),
                20,
                0.5, 0.5, 0.5,
                0.05
        );

        player.getWorld().spawnParticle(
                Particle.SOUL,
                player.getLocation().add(0, 1, 0),
                15,
                0.3, 0.5, 0.3,
                0.02
        );

        // Sound effects
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.3f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
    }
}
