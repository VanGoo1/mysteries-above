package me.vangoo.beyonders;

import me.vangoo.LotmPlugin;
import me.vangoo.abilities.Ability;
import me.vangoo.abilities.AbilityManager;
import me.vangoo.pathways.PathwayManager;
import me.vangoo.utils.BossBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

public class BeyonderManager implements Listener {
    private final LotmPlugin plugin;
    private final PathwayManager pathwayManager;
    private final AbilityManager abilityManager;
    private final BossBarUtil bossBarUtil;
    private Map<UUID, Beyonder> beyonders;

    public BeyonderManager(LotmPlugin plugin, PathwayManager pathwayManager, AbilityManager abilityManager, BossBarUtil bossBarUtil) {
        this.plugin = plugin;
        this.pathwayManager = pathwayManager;
        this.abilityManager = abilityManager;
        this.bossBarUtil = bossBarUtil;
        loadBeyonders();
        startSpiritualityRegeneration();
    }

    private void loadBeyonders() {
        this.beyonders = new HashMap<>();
    }

    private void startSpiritualityRegeneration() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            beyonders.values().forEach(this::regenerateSpirituality);
        }, 20L, 20L); // Кожну секунду
    }

    private void regenerateSpirituality(Beyonder beyonder) {
        if (beyonder.getSpirituality() < beyonder.getMaxSpirituality()) {
            beyonder.IncrementSpirituality(1);
            updateSpiritualityBar(beyonder);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;
        if (beyonder.getSequence() != -1) {
            createSpiritualityBar(player, beyonder);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bossBarUtil.removePlayer(event.getPlayer());
    }

    public void createSpiritualityBar(Player player, Beyonder beyonder) {
        String title = String.format("Духовність: %d/%d",
                beyonder.getSpirituality(), beyonder.getMaxSpirituality());

        double progress = 0;
        if (beyonder.getMaxSpirituality() != 0)
            progress = (double) beyonder.getSpirituality() / beyonder.getMaxSpirituality();

        bossBarUtil.addPlayer(player, title, BarColor.BLUE, BarStyle.SOLID, progress);
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
        Beyonder beyonder = GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;

        Ability ability = abilityManager.GetAbilityFromItem(item, beyonder);
        if (ability == null) return;
        event.setCancelled(true);
        boolean success = abilityManager.executeAbility(player, beyonder, ability);
        if (success) {
//            player.setCooldown(item, ability.getCooldown() * 20);
            beyonder.DecrementSpirituality(ability.getSpiritualityCost());
            updateSpiritualityBar(beyonder);
        }
    }

    public void updateSpiritualityBar(Beyonder beyonder) {
        Player player = Bukkit.getPlayer(beyonder.getPlayerId());
        if (player == null || !player.isOnline()) return;

        String title = String.format("Духовність: %d/%d",
                beyonder.getSpirituality(), beyonder.getMaxSpirituality());
        double progress = (double) beyonder.getSpirituality() / beyonder.getMaxSpirituality();

        bossBarUtil.setTitle(player, title);
        bossBarUtil.setProgress(player, progress);
    }

    @Nullable
    public Beyonder GetBeyonder(UUID playerId) {
        return beyonders.get(playerId);
    }

    public void AddBeyonder(Beyonder beyonder) {
        beyonders.put(beyonder.getPlayerId(), beyonder);
    }


}
