package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.infrastructure.market.GatheringVenueProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Побутові події зборів: повернення при вході, скасування торгів при виході,
 * анонімний чат зали («Незнайомець №N»), захист блоків світу-заглушки.
 */
public class GatheringListener implements Listener {

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private final GatheringVenueProvider venueProvider;

    public GatheringListener(Plugin plugin, GatheringService gatheringService,
                             GatheringVenueProvider venueProvider) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
        this.venueProvider = venueProvider;
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

    /**
     * Анонімний чат зали. NORMAL: ChatPromptService (LOWEST, Task 15) уже забрав
     * свої відповіді й скасував подію.
     */
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
