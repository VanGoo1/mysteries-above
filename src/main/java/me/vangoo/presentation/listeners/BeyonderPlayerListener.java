package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
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

import java.util.logging.Logger;

/**
 * Listener for beyonder player events.
 * Handles join/quit events and ability usage through items.
 */
public class BeyonderPlayerListener implements Listener {
    private final BeyonderService beyonderService;
    private final BossBarUtil bossBarUtil;
    private final AbilityExecutor abilityExecutor;
    private final AbilityItemFactory abilityItemFactory;
    private final Logger logger;

    public BeyonderPlayerListener(
            BeyonderService beyonderService,
            BossBarUtil bossBarUtil,
            AbilityExecutor abilityExecutor,
            AbilityItemFactory abilityItemFactory,
            Logger logger
    ) {
        this.beyonderService = beyonderService;
        this.bossBarUtil = bossBarUtil;
        this.abilityExecutor = abilityExecutor;
        this.abilityItemFactory = abilityItemFactory;
        this.logger = logger;
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

        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        Ability ability = null;

        if (mainHandItem.hasItemMeta()) {
            ability = abilityItemFactory.getAbilityFromItem(mainHandItem, beyonder);
        }

        if (ability == null && offHandItem.hasItemMeta()) {
            ability = abilityItemFactory.getAbilityFromItem(offHandItem, beyonder);
        }

        if (ability == null) {
            return;
        }

        event.setCancelled(true);

        AbilityResult result = abilityExecutor.execute(beyonder, ability);

        showResultToPlayer(player, result);
        logger.info(result.toString());
    }

    private void showResultToPlayer(Player player, AbilityResult result) {
        if (result.isSuccess()) {
            // Success cases
            if (result.hasSanityPenalty()) {
                // Success with penalty
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.YELLOW + "⚠ Здібність виконана, але з наслідками"));
            }
            // Pure success - no message needed (UI auto-updates)
        } else {
            // Failure case
            String message = result.getMessage() != null
                    ? result.getMessage()
                    : "Не вдалося використати здібність";
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.RED + message));
        }
    }
}
