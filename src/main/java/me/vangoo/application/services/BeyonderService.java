package me.vangoo.application.services;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.IBeyonderRepository;
import me.vangoo.infrastructure.ui.BossBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.util.UUID;

import static org.bukkit.Bukkit.getPlayer;

public class BeyonderService {
    private final IBeyonderRepository repository;
    private final BossBarUtil bossBarUtil;

    public BeyonderService(IBeyonderRepository repository, BossBarUtil bossBarUtil) {
        this.repository = repository;
        this.bossBarUtil = bossBarUtil;
    }

    /**
     * Отримати beyonder'а
     */
    public Beyonder getBeyonder(UUID playerId) {
        return repository.get(playerId);
    }

    /**
     * Створити нового beyonder'а
     */
    public void createBeyonder(Beyonder beyonder) {
        repository.add(beyonder);

        // Створити UI (boss bar)
        Player player = getPlayer(beyonder.getPlayerId());
        if (player != null && player.isOnline()) {
            createSpiritualityBar(player, beyonder);
        }
    }

    /**
     * Видалити beyonder'а
     */
    public void removeBeyonder(UUID playerId) {
        repository.remove(playerId);

        // Видалити UI
        Player player = getPlayer(playerId);
        if (player != null && player.isOnline()) {
            bossBarUtil.removePlayer(player);
        }
    }

    /**
     * Оновити beyonder'а
     */
    public void updateBeyonder(Beyonder beyonder) {
        repository.update(beyonder.getPlayerId(), beyonder);
    }

    /**
     * Регенерувати духовність
     * Викликається з scheduler
     */
    public void regenerateAll() {
        repository.getAll().values().forEach(this::regenerateSpirituality);
    }

    private void regenerateSpirituality(Beyonder beyonder) {
        Player player = getPlayer(beyonder.getPlayerId());
        if (player == null || !player.isOnline())
            return;
        if (!beyonder.getSpirituality().isFull()) {
            beyonder.regenerateSpirituality();
            updateSpiritualityBar(player, beyonder);
        }
    }

    public void createSpiritualityBar(Player player, Beyonder beyonder) {
        String title = String.format("Духовність: %d/%d",
                beyonder.getSpiritualityValue(),
                beyonder.getMaxSpirituality());

        double progress = beyonder.getSpirituality().getPercentage();

        bossBarUtil.addPlayer(player, title, BarColor.BLUE, BarStyle.SOLID, progress);
    }

    private void updateSpiritualityBar(Player player, Beyonder beyonder) {
        if (player == null || !player.isOnline())
            return;

        String title = String.format("Духовність: %d/%d",
                beyonder.getSpiritualityValue(),
                beyonder.getMaxSpirituality());

        double progress = beyonder.getSpirituality().getPercentage();

        bossBarUtil.setTitle(player, title);
        bossBarUtil.setProgress(player, progress);
    }
}
