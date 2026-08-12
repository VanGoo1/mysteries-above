package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.organizations.ShrineService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Двері між селом і кишеньковим світом храмів: ПКМ по вівтарю шрайна веде туди, ПКМ по
 * каменю-виходу — назад.
 *
 * <p>Sneak пропускаємо навмисно — присідання скрізь у плагіні зарезервоване під інші дії
 * (шпигунство орденів, службові кліки), і випадковий телепорт під час будівництва біля
 * святині був би прикрим.
 */
public class ShrineListener implements Listener {

    private final ShrineService shrines;

    public ShrineListener(ShrineService shrines) {
        this.shrines = shrines;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (shrines.isChurchWorld(block.getWorld())) {
            if (shrines.isExitPad(block.getLocation())) {
                event.setCancelled(true);
                shrines.leave(player);
            }
            return;
        }
        shrines.shrineAt(block).ifPresent(shrine -> {
            event.setCancelled(true);
            shrines.enter(player, shrine);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        shrines.restoreIfStranded(event.getPlayer());
    }
}
