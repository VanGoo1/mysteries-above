package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.infrastructure.market.GatheringVenueProvider;
import me.vangoo.infrastructure.ui.MarketMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Побутові події зборів: повернення при вході, скасування торгів при виході,
 * анонімний чат зали («Незнайомець №N»), захист блоків світу-заглушки.
 */
public class GatheringListener implements Listener {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Збори] " + ChatColor.RESET;

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private final GatheringVenueProvider venueProvider;
    private final MarketMenu marketMenu;

    public GatheringListener(Plugin plugin, GatheringService gatheringService,
                             GatheringVenueProvider venueProvider, MarketMenu marketMenu) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
        this.venueProvider = venueProvider;
        this.marketMenu = marketMenu;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Наступний тік: інвентар/світ гравця вже повністю завантажені
        Bukkit.getScheduler().runTask(plugin,
                () -> gatheringService.handleJoin(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gatheringService.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!gatheringService.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo() != null && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFrozenSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return; // цікавить лише момент, коли гравець ПОЧАВ присідати
        }
        if (gatheringService.isFrozen(event.getPlayer().getUniqueId())) {
            gatheringService.skipBriefing(event.getPlayer());
        }
    }

    @EventHandler
    public void onLecternClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.LECTERN
                || !venueProvider.isVenueWorld(event.getClickedBlock().getWorld())) {
            return;
        }
        event.setCancelled(true); // глушить дефолтне GUI кафедри
        Player player = event.getPlayer();
        if (!gatheringService.isOpenParticipant(player)) {
            return;
        }
        if (!gatheringService.hasBeenBriefed(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Спершу вислухайте Посередника.");
            return;
        }
        marketMenu.openMain(player);
    }

    /** Анонімний чат зали: підміняє репліки учасників на «Незнайомець №N». */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        // Async-потік: читаємо лише потокобезпечний сигнал (UUID-перевантаження),
        // усе доступ до session/aliases відкладаємо в головний потік нижче.
        if (!gatheringService.isOpenParticipant(sender.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!gatheringService.isOpenParticipant(sender)) {
                return; // збір закрився, поки повідомлення летіло
            }
            String line = ChatColor.DARK_GRAY + gatheringService.aliasOf(sender.getUniqueId())
                    + ChatColor.GRAY + ": " + message;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (gatheringService.isOpenParticipant(online)) {
                    online.sendMessage(line);
                }
            }
            plugin.getLogger().info("[Gathering chat] " + sender.getName() + ": " + message);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onVenueDamage(EntityDamageByEntityEvent event) {
        if (!venueProvider.isVenueWorld(event.getEntity().getWorld())) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof Player) || victim.hasMetadata("NPC")) {
            return; // NPC-Посередник або не гравець — не рахуємо
        }
        if (gatheringService.isOpenParticipant(attacker)) {
            gatheringService.recordViolation(attacker);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelInVenue(event.getPlayer().getUniqueId(), event.getBlock().getWorld(),
                () -> event.setCancelled(true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelInVenue(event.getPlayer().getUniqueId(), event.getBlock().getWorld(),
                () -> event.setCancelled(true));
    }

    private void cancelInVenue(UUID playerId, org.bukkit.World world, Runnable cancel) {
        if (venueProvider.isVenueWorld(world)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.hasPermission("mysteriesabove.admin")) {
                cancel.run();
            }
        }
    }
}
