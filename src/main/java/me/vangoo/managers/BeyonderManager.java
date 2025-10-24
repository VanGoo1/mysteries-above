package me.vangoo.managers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.Beyonder;
import me.vangoo.infrastructure.IBeyonderStorage;
import me.vangoo.utils.BossBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;

public class BeyonderManager {
    private final MysteriesAbovePlugin plugin;
    private final BossBarUtil bossBarUtil;
    private IBeyonderStorage beyonderStorage;

    public BeyonderManager(MysteriesAbovePlugin plugin, BossBarUtil bossBarUtil, IBeyonderStorage beyonderStorage) {
        this.plugin = plugin;
        this.bossBarUtil = bossBarUtil;
        this.beyonderStorage = beyonderStorage;
        startSpiritualityRegeneration();
    }

//    private void loadBeyonders() {
//        this.beyonders = new HashMap<>();
//    }

    private void startSpiritualityRegeneration() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            beyonderStorage.getAll().values().forEach(this::regenerateSpirituality);
        }, 20L, 20L); // Кожну секунду
    }

    private void regenerateSpirituality(Beyonder beyonder) {
        if (beyonder.getSpirituality() < beyonder.getMaxSpirituality()) {
            beyonder.IncrementSpirituality(1);
            updateSpiritualityBar(beyonder);
        }
    }

    public void createSpiritualityBar(Player player, Beyonder beyonder) {
        String title = String.format("Духовність: %d/%d",
                beyonder.getSpirituality(), beyonder.getMaxSpirituality());

        double progress = 0;
        if (beyonder.getMaxSpirituality() != 0)
            progress = (double) beyonder.getSpirituality() / beyonder.getMaxSpirituality();

        bossBarUtil.addPlayer(player, title, BarColor.BLUE, BarStyle.SOLID, progress);
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
        return beyonderStorage.get(playerId);
    }

    public void AddBeyonder(Beyonder beyonder) {
        beyonderStorage.add(beyonder);
    }
    public void RemoveBeyonder(UUID playerId) {
        beyonderStorage.remove(playerId);
        // Також прибираємо бос-бар, якщо гравець онлайн
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            bossBarUtil.removePlayer(player);
        }
    }

    public void updateBeyonder(Beyonder beyonder) {
        beyonderStorage.update(beyonder.getPlayerId(), beyonder);
    }
}
