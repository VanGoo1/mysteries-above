package me.vangoo.listeners;

import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import me.vangoo.managers.AbilityManager;
import me.vangoo.managers.BeyonderManager;
import me.vangoo.managers.RampagerManager;
import me.vangoo.utils.BossBarUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class BeyonderPlayerListener implements Listener {
    private final BeyonderManager beyonderManager;
    private final BossBarUtil bossBarUtil;
    private final AbilityManager abilityManager;

    public BeyonderPlayerListener(BeyonderManager beyonderManager, BossBarUtil bossBarUtil, AbilityManager abilityManager) {
        this.beyonderManager = beyonderManager;
        this.bossBarUtil = bossBarUtil;
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;
        if (beyonder.getSequence() != -1) {
            beyonderManager.createSpiritualityBar(player, beyonder);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bossBarUtil.removePlayer(event.getPlayer());
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                break;
            default:
                return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;

        Ability ability = abilityManager.GetAbilityFromItem(item, beyonder);
        if (ability == null) return;
        event.setCancelled(true);
        boolean success = abilityManager.executeAbility(player, beyonder, ability);
        if (success) {
//            player.setCooldown(item, ability.getCooldown() * 20);
            beyonderManager.updateSpiritualityBar(beyonder);
        }
    }
}
