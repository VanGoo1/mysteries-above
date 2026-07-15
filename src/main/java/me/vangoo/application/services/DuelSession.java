package me.vangoo.application.services;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Живий стан однієї дуелі: опонент, куди повернути гравця, його попередній режим.
 * Володіє власним BukkitTask (таймаут). Ніколи не static — реєстр у ChurchDuelService.
 */
public class DuelSession {

    private static final long TIMEOUT_TICKS = 20L * 60 * 5; // 5 хв

    private final UUID playerId;
    private final String institutionId;
    private final UUID opponentUuid;
    private final Location returnLocation;
    private final GameMode previousGameMode;
    private final Runnable onTimeout;

    private BukkitTask task;

    public DuelSession(UUID playerId, String institutionId, UUID opponentUuid,
                       Location returnLocation, GameMode previousGameMode, Runnable onTimeout) {
        this.playerId = playerId;
        this.institutionId = institutionId;
        this.opponentUuid = opponentUuid;
        this.returnLocation = returnLocation;
        this.previousGameMode = previousGameMode;
        this.onTimeout = onTimeout;
    }

    public void start(org.bukkit.plugin.Plugin plugin) {
        task = Bukkit.getScheduler().runTaskLater(plugin, onTimeout, TIMEOUT_TICKS);
    }

    /** Скасовує таймаут і прибирає опонента зі світу. */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Entity opponent = Bukkit.getEntity(opponentUuid);
        if (opponent != null && !opponent.isDead()) {
            opponent.remove();
        }
    }

    public UUID playerId() { return playerId; }
    public String institutionId() { return institutionId; }
    public UUID opponentUuid() { return opponentUuid; }
    public Location returnLocation() { return returnLocation; }
    public GameMode previousGameMode() { return previousGameMode; }
}
