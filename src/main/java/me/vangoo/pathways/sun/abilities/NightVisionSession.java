package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Жива сесія «Нічне бачення» — той самий патерн, що {@link DaytimeSession}: тікає власним
 * Bukkit-таском, раз на вікно списує духовність і оновлює ефект; вичерпано (або власник
 * офлайн) — сесія гасить сама себе.
 */
final class NightVisionSession {

    static final long REFRESH_PERIOD_TICKS = 100L; // 5 секунд
    private static final int EFFECT_DURATION_TICKS = 140; // трохи довше за період оновлення

    private final UUID ownerId;
    private final int periodicCost;
    private final IBeyonderContext beyonderContext;
    private final Map<UUID, NightVisionSession> sessions;
    private BukkitTask task;

    NightVisionSession(UUID ownerId, int periodicCost, IBeyonderContext beyonderContext,
                        Map<UUID, NightVisionSession> sessions) {
        this.ownerId = ownerId;
        this.periodicCost = periodicCost;
        this.beyonderContext = beyonderContext;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    void applyNow() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        applyVision(owner);
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            cancel();
            return;
        }

        Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
        if (beyonder == null || beyonder.getSpirituality().current() < periodicCost) {
            owner.sendActionBar(Component.text("✗ Духовність вичерпана — нічне бачення згасає"));
            cancel();
            return;
        }
        beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));

        applyVision(owner);
    }

    /** Гасить бачення, прибирає з активних, зупиняє таск. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.removePotionEffect(PotionEffectType.NIGHT_VISION);
            owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0f, 0.6f);
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void applyVision(Player owner) {
        owner.removePotionEffect(PotionEffectType.DARKNESS);
        owner.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, EFFECT_DURATION_TICKS, 0));
    }
}
