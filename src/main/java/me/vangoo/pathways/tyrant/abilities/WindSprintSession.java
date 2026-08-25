package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Жива сесія «Вітряний біг» — той самий патерн, що {@link WindFlightSession}: тікає власним
 * Bukkit-таском, раз на секунду списує духовність і поновлює швидкість; вичерпано (або власник
 * офлайн) — вітер стихає сам. Слід вітру стелиться біля ніг, поки гравець рухається.
 */
final class WindSprintSession {

    static final long REFRESH_PERIOD_TICKS = 2L;   // швидкий такт — плавний слід вітру
    private static final int COST_EVERY = 10;      // ціна раз на 20т (1 с)
    private static final int SPEED_AMPLIFIER = 4;  // Швидкість V — удвічі швидший біг
    private static final int EFFECT_DURATION_TICKS = 60; // довше за такт ціни, щоб не блимало

    private final UUID ownerId;
    private final int periodicCost;
    private final IBeyonderContext beyonderContext;
    private final Map<UUID, WindSprintSession> sessions;
    private BukkitTask task;
    private int runs;

    WindSprintSession(UUID ownerId, int periodicCost, IBeyonderContext beyonderContext,
                      Map<UUID, WindSprintSession> sessions) {
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
        applySpeed(owner);
        owner.getWorld().spawnParticle(Particle.CLOUD, owner.getLocation(), 12, 0.3, 0.1, 0.3, 0.06);
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            cancel();
            return;
        }

        trail(owner);

        if (++runs % COST_EVERY == 0) {
            Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
            if (beyonder == null || beyonder.getSpirituality().current() < periodicCost) {
                owner.sendActionBar(Component.text("✗ Духовність вичерпана — вітер стихає"));
                cancel();
                return;
            }
            beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));
            applySpeed(owner);
        }
    }

    /** Знімає швидкість, прибирає з активних, зупиняє таск. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            // Знімає і слабшу Швидкість пасиву фізики — той поновить її своїм тактом (≤5 с).
            owner.removePotionEffect(PotionEffectType.SPEED);
            owner.playSound(owner.getLocation(), Sound.ENTITY_PHANTOM_FLAP, SoundCategory.MASTER, 1.0f, 0.7f);
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** Вітряні пасма біля ніг, позаду напряму руху — слід розгону. */
    private void trail(Player owner) {
        Vector vel = owner.getVelocity();
        if (vel.lengthSquared() < 0.02) return; // стоїть на місці — сліду немає
        Location at = owner.getLocation().add(0, 0.2, 0)
                .add(vel.normalize().multiply(-0.6));
        owner.getWorld().spawnParticle(Particle.CLOUD, at, 2, 0.1, 0.05, 0.1, 0.01);
    }

    private void applySpeed(Player owner) {
        owner.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, EFFECT_DURATION_TICKS, SPEED_AMPLIFIER, true, false));
    }
}
