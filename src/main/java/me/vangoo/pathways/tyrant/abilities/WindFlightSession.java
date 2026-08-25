package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
 * Жива сесія «Політ на вітрі» — той самий патерн, що {@code NightVisionSession}: тікає
 * власним Bukkit-таском, раз на секунду списує духовність і підтримує рух; вичерпано (або
 * власник офлайн) — сесія гасить сама себе. Режим {@link Mode#FLIGHT} дає вільний політ
 * (ширяння на місці — його властивість), {@link Mode#GLIDE} — повільне опускання.
 */
final class WindFlightSession {

    static final long REFRESH_PERIOD_TICKS = 2L; // швидкий такт — плавний вітряний шлейф
    private static final int COST_EVERY = 10;      // ціна раз на 20т (1 с)
    private static final int SLOW_FALL_TICKS = 40; // довше за такт, щоб не блимало

    enum Mode {
        FLIGHT("Політ"), GLIDE("Ковзання");
        private final String displayName;
        Mode(String displayName) { this.displayName = displayName; }
        String displayName() { return displayName; }
    }

    private final UUID ownerId;
    private final int periodicCost;
    private final IBeyonderContext beyonderContext;
    private final Map<UUID, WindFlightSession> sessions;
    private volatile Mode mode = Mode.FLIGHT;
    private BukkitTask task;
    private int runs;

    WindFlightSession(UUID ownerId, int periodicCost, IBeyonderContext beyonderContext,
                      Map<UUID, WindFlightSession> sessions, Mode initialMode) {
        this.ownerId = ownerId;
        this.periodicCost = periodicCost;
        this.beyonderContext = beyonderContext;
        this.sessions = sessions;
        this.mode = initialMode;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Перемикає режим і одразу застосовує наслідок (вмикає/знімає вільний політ). */
    Mode switchMode() {
        mode = (mode == Mode.FLIGHT) ? Mode.GLIDE : Mode.FLIGHT;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            if (mode == Mode.GLIDE) clearFlight(owner);
            else engageFlight(owner);
        }
        return mode;
    }

    void applyNow() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        if (mode == Mode.FLIGHT) engageFlight(owner);
        burst(owner); // початковий порив вітру позаду гравця
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            cancel();
            return;
        }

        trail(owner);
        maintainFlight(owner);

        // Ціна списується раз на секунду; вичерпано — вітер стихає.
        if (++runs % COST_EVERY == 0) {
            Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
            if (beyonder == null || beyonder.getSpirituality().current() < periodicCost) {
                owner.sendActionBar(Component.text("✗ Духовність вичерпана — вітер стихає"));
                cancel();
                return;
            }
            beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));
        }
    }

    /** Гасить політ, прибирає з активних, зупиняє таск. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            clearFlight(owner);
            owner.removePotionEffect(PotionEffectType.SLOW_FALLING);
            owner.playSound(owner.getLocation(), Sound.ENTITY_PHANTOM_FLAP, SoundCategory.MASTER, 1.0f, 0.7f);
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** Легкі вітряні пасма, що лишаються ПОЗАДУ гравця (проти напряму руху) — «слід вітру». */
    private void trail(Player owner) {
        Location at = owner.getLocation().add(0, 1.0, 0);
        Vector vel = owner.getVelocity();
        if (vel.lengthSquared() > 0.02) {
            at.add(vel.normalize().multiply(-0.7)); // зсув назад по руху
        }
        owner.getWorld().spawnParticle(Particle.CLOUD, at, 3, 0.12, 0.12, 0.12, 0.01);
    }

    /** Разовий порив вітру позаду при зльоті. */
    private void burst(Player owner) {
        Location behind = owner.getLocation().add(0, 1.0, 0);
        Vector dir = owner.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 1.0e-4) {
            behind.subtract(dir.normalize().multiply(0.8));
        }
        owner.getWorld().spawnParticle(Particle.CLOUD, behind, 12, 0.25, 0.25, 0.25, 0.05);
    }

    private void maintainFlight(Player owner) {
        if (mode == Mode.FLIGHT) {
            // ponytail: щосекунди повертаємо дозвіл польоту; якщо політ ззовні заборонено
            // (напр. Заборона Сил зриває його вчетверо частіше), гравець падає — і його
            // ловить ковзання нижче, тож лишається саме ковзання, як і задумано.
            if (managesFlight(owner)) {
                owner.setAllowFlight(true);
            }
            if (!owner.isFlying() && !owner.isOnGround() && owner.getVelocity().getY() < -0.3) {
                applySlowFall(owner);
            }
        } else { // GLIDE — вітер лагідно опускає
            applySlowFall(owner);
        }
    }

    private void engageFlight(Player owner) {
        if (!managesFlight(owner)) return;
        owner.setAllowFlight(true);
        owner.setFlying(true);
    }

    private void clearFlight(Player owner) {
        if (!managesFlight(owner)) return;
        owner.setFlying(false);
        owner.setAllowFlight(false);
    }

    private void applySlowFall(Player owner) {
        owner.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, SLOW_FALL_TICKS, 0, true, false));
    }

    /** У креативі/спостерігачі політ уже вбудований — не чіпаємо. */
    private boolean managesFlight(Player owner) {
        GameMode gm = owner.getGameMode();
        return gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR;
    }
}
