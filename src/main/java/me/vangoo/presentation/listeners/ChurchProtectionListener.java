package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.organizations.ChurchSiteService;
import me.vangoo.infrastructure.organizations.ShrineService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Храм не можна зруйнувати. Межі будівлі знає сайт (`ChurchSiteRepository.Box`,
 * порахований інструментом із розміру NBT й принесений міткою), тож захист переживає
 * рестарт разом із `church-sites.json`.
 *
 * Три джерела меж, і всі три сходяться в {@link #isProtected}:
 * <ol>
 *   <li>сайт храму (`Box` із `church-sites.json`) — для храмів, прив'язаних у звичайному світі;</li>
 *   <li>шрайн у селі (`Box` із його мітки) — щоб святиню не розібрали й не забудували;</li>
 *   <li>увесь кишеньковий світ храмів — там правило просте й тотальне, без жодних боксів.</li>
 * </ol>
 *
 * У храмі й на шрайні заборонено і ЛАМАТИ, і СТАВИТИ: святиня — не будмайданчик.
 * Адмін (`mysteriesabove.admin`) проходить крізь захист, інакше `/church unbind` і ручні
 * правки стали б неможливими.
 *
 * Священики окремого захисту не потребують: Citizens тримає їх `setProtected(true)`,
 * а замах ордену б'є не уроном, а власним сценарієм в `OrderListener`.
 */
public class ChurchProtectionListener implements Listener {

    private static final String BYPASS = "mysteriesabove.admin";

    private final ChurchSiteService sites;
    private final ShrineService shrines;

    public ChurchProtectionListener(ChurchSiteService sites, ShrineService shrines) {
        this.sites = sites;
        this.shrines = shrines;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.DARK_RED
                    + "Камінь храму не піддається — щось тримає його міцніше за розчин.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.DARK_RED
                    + "Тут нічого не поставити — освячене місце не приймає чужої руки.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Ендермени, сільверфіші, равагери — теж знищення блоку, лише чужими руками. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Вода й лава ззовні не мають вимивати начиння храму. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isProtected(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        cancelIfAny(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        cancelIfAny(event.getBlocks(), event);
    }

    private void cancelIfAny(List<Block> blocks, org.bukkit.event.Cancellable event) {
        for (Block block : blocks) {
            if (isProtected(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean denied(Player player, Block block) {
        return !player.hasPermission(BYPASS) && isProtected(block.getLocation());
    }

    /**
     * Порядок перевірок — від найдешевшої до найдорожчої: назва світу, потім лінійний
     * прохід по ≤10 сайтах, і лише насамкінець пошук мітки шрайна серед сутностей поблизу.
     */
    private boolean isProtected(Location loc) {
        if (loc == null) {
            return false;
        }
        return shrines.isChurchWorld(loc.getWorld())
                || sites.isProtected(loc)
                || shrines.isProtected(loc);
    }
}
