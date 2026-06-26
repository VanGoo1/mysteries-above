package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityContextFactory;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Presentation Listener: Handles right-click on the "Вийти з маріонетки" echo-shard item.
 * During possession the normal ability trigger is unavailable (inventory/identity swapped),
 * so this listener drives the exit directly.
 */
public class MarionetteExitListener implements Listener {

    private final AbilityContextFactory abilityContextFactory;
    private final PathwayManager pathwayManager;

    public MarionetteExitListener(AbilityContextFactory abilityContextFactory, PathwayManager pathwayManager) {
        this.abilityContextFactory = abilityContextFactory;
        this.pathwayManager = pathwayManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        boolean isExit = MarionettistControl.isSwapBackItem(item);
        boolean isMenu = MarionettistControl.isSwapMenuItem(item);
        if (item == null || (!isExit && !isMenu)) {
            return;
        }

        event.setCancelled(true);

        Ability a = pathwayManager.findAbilityInAllPathways(MarionettistControl.IDENTITY);
        if (!(a instanceof MarionettistControl mc)) {
            return;
        }

        IAbilityContext ctx = abilityContextFactory.createContext(event.getPlayer());
        if (isMenu) {
            mc.openSwapMenu(ctx);   // швидке перемикання між маріонетками
        } else {
            mc.exitIfPossessing(ctx);
        }
    }
}
