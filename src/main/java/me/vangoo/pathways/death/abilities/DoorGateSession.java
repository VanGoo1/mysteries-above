package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Двері в Загробний Світ (Death, Посл. 5, режими {@code DRAG}/{@code PURGE}/{@code BIND})
 * поставлені в точці, куди дивиться гравець — не на цілі-сутності. Брама стоїть і діє
 * {@code durationSeconds}, повторюючи {@code tickEffect} щотакту; {@code DESCEND} лишається
 * на власній {@link UnderworldDescentSession} (інша форма — зона, не прямокутна брама).
 *
 * <p>Сесія навмисно "німа": усю специфіку режиму (кого й як тягне/чистить/скручує) несе
 * {@code tickEffect}, зібраний у {@code DoorToTheUnderworld} під час касту — сесія лише
 * малює браму й повторює його, поки не спливе строк.
 */
final class DoorGateSession {

    static final long TICK_PERIOD_TICKS = 20L;

    private final UUID ownerId;
    private final Location gateBase;
    private final Vector facing;
    private final double gateWidth;
    private final double gateHeight;
    private final Color color;
    private final long expiresAtMillis;
    private final IVisualEffectsContext effects;
    private final Runnable tickEffect;
    private final Map<UUID, DoorGateSession> sessions;

    private BukkitTask task;

    DoorGateSession(UUID ownerId, Location gateBase, Vector facing, double gateWidth, double gateHeight,
                     Color color, int durationSeconds, IVisualEffectsContext effects,
                     Runnable tickEffect, Map<UUID, DoorGateSession> sessions) {
        this.ownerId = ownerId;
        this.gateBase = gateBase.clone();
        this.facing = facing.clone();
        this.gateWidth = gateWidth;
        this.gateHeight = gateHeight;
        this.color = color;
        this.expiresAtMillis = System.currentTimeMillis() + durationSeconds * 1000L;
        this.effects = effects;
        this.tickEffect = tickEffect;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    void tick() {
        if (System.currentTimeMillis() >= expiresAtMillis || gateBase.getWorld() == null) {
            cancel();
            return;
        }

        effects.playUnderworldGate(gateBase, facing, gateWidth, gateHeight, color, (int) TICK_PERIOD_TICKS);
        tickEffect.run();
    }

    /** Гасне сама по спливу строку; повторний каст того самого режиму замінює сесію заздалегідь. */
    void cancel() {
        sessions.remove(ownerId, this);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
