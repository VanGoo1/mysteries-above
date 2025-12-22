package me.vangoo.presentation.listeners;

import me.vangoo.application.abilities.AbilityExecutionResult;
import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.RampageEffectsHandler;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Beyonder;

import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.ui.BossBarUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for beyonder player events.
 * Handles join/quit events and ability usage through items.
 */
public class BeyonderPlayerListener implements Listener {
    private final BeyonderService beyonderService;
    private final BossBarUtil bossBarUtil;
    private final AbilityExecutor abilityExecutor;
    private final AbilityItemFactory abilityItemFactory;

    public BeyonderPlayerListener(
            BeyonderService beyonderService,
            BossBarUtil bossBarUtil,
            AbilityExecutor abilityExecutor,
            AbilityItemFactory abilityItemFactory
    ) {
        this.beyonderService = beyonderService;
        this.bossBarUtil = bossBarUtil;
        this.abilityExecutor = abilityExecutor;
        this.abilityItemFactory = abilityItemFactory;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }
        beyonderService.createSpiritualityBar(player, beyonder);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bossBarUtil.removePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-clicks
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                break;
            default:
                return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        // Get ability from item
        Ability ability = abilityItemFactory.getAbilityFromItem(item, beyonder);
        if (ability == null) {
            return;
        }

        event.setCancelled(true);

        // Execute ability through application service
        AbilityExecutionResult result = abilityExecutor.execute(beyonder, ability);

        // Handle result
        handleExecutionResult(player, beyonder, result);

        // Save beyonder state
        beyonderService.updateBeyonder(beyonder);
    }

    /**
     * Handle ability execution result - presentation logic
     */
    private void handleExecutionResult(Player player, Beyonder beyonder, AbilityExecutionResult result) {
        if (result.isSuccess()) {
            // Success - UI is automatically updated by BeyonderService.updateBeyonder()
            return;
        }

        // Failure - show error message
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.RED + result.message()));

    }
}
