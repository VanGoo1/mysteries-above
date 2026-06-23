package me.vangoo.pathways.door.abilities;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IEventContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Жива сесія «Сонне Провидіння» — один активний сон одного власника.
 * <p>
 * Найскладніший зріз: життєвий цикл сплетений із шиною подій. Сесія володіє двома тасками
 * (стеження за ціллю + автозавершення) і двома тимчасовими підписками (вихід/перезахід гравця),
 * а на завершення відновлює режим гри й позицію.
 * <p>
 * Тікає через Bukkit напряму. Єдина залежність, яку сесія тримає — {@link IEventContext}: це
 * <b>глобальний</b> сервіс шини подій (усі методи беруть явні id, жодного caster-стану), тож це
 * не порушує правило «не захоплювати контекст одного кастера» — на відміну від повного
 * {@code IAbilityContext}, який віддає {@code getCasterId()/getCasterPlayer()}.
 * <p>
 * До рефактора весь цей стан жив у локальних {@code trackingTask[]}/{@code endTask[]}/{@code finished[]}
 * усередині здібності й ніде не трекався — тобто при вимкненні плагіна гравець міг лишитися
 * назавжди у SPECTATOR. Тепер сесію тримає інстанс-реєстр у {@link DivinationArts}, а {@link #cancel()}
 * (через {@code cleanUp()}) відновлює гравця.
 */
public final class DreamVisionSession {

    private static final long TRACKING_PERIOD_TICKS = 5L;
    private static final long DURATION_TICKS = 15 * 20L;
    private static final int QUIT_LISTENER_TICKS = 15 * 20;
    private static final int REJOIN_LISTENER_TICKS = 60 * 20; // більший час на перезахід
    private static final double MAX_TETHER_DISTANCE = 15.0;

    private final UUID ownerId;
    private final UUID targetId;
    private final GameMode originalMode;
    private final Location originalLoc;
    private final IEventContext events;

    private BukkitTask trackingTask;
    private BukkitTask endTask;
    private boolean finished = false;

    public DreamVisionSession(UUID ownerId, UUID targetId, GameMode originalMode,
                              Location originalLoc, IEventContext events) {
        this.ownerId = ownerId;
        this.targetId = targetId;
        this.originalMode = originalMode;
        this.originalLoc = originalLoc.clone();
        this.events = events;
    }

    public UUID ownerId() {
        return ownerId;
    }

    /** Запускає життєвий цикл: підписки на події + таски стеження і автозавершення. */
    public void start() {
        MysteriesAbovePlugin plugin = JavaPlugin.getPlugin(MysteriesAbovePlugin.class);

        // Вихід під час сну: НЕ відновлюємо тут — лишаємо SPECTATOR, щоб rejoin-хендлер впорався.
        events.subscribeToTemporaryEvent(
                ownerId,
                PlayerQuitEvent.class,
                event -> event.getPlayer().getUniqueId().equals(ownerId),
                event -> {
                    finished = true;
                    cancelTasks();
                },
                QUIT_LISTENER_TICKS
        );

        // Перезахід: якщо гравець досі SPECTATOR — відновлюємо режим і позицію.
        events.subscribeToTemporaryEvent(
                ownerId,
                PlayerJoinEvent.class,
                event -> event.getPlayer().getUniqueId().equals(ownerId),
                event -> {
                    Player rejoined = event.getPlayer();
                    if (rejoined.getGameMode() == GameMode.SPECTATOR) {
                        rejoined.setGameMode(originalMode);
                        if (originalLoc.getWorld() != null) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (rejoined.isOnline()) rejoined.teleport(originalLoc);
                            }, 5L);
                        }
                        rejoined.playSound(rejoined.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT,
                                SoundCategory.MASTER, 1f, 1.5f);
                        rejoined.sendMessage(ChatColor.YELLOW + "✦ Ви повернулись зі сну після перезаходу");
                    }
                    events.unsubscribeAll(ownerId);
                },
                REJOIN_LISTENER_TICKS
        );

        // Стеження: тримаємо власника біля цілі.
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::trackTick, 0L, TRACKING_PERIOD_TICKS);

        // Автозавершення через 15 секунд.
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::finish, DURATION_TICKS);
    }

    /** Примусове завершення (повторний каст / cleanUp). Ідемпотентне, відновлює гравця. */
    public void cancel() {
        finish();
    }

    private void trackTick() {
        if (finished) return;

        Player owner = Bukkit.getPlayer(ownerId);
        Player target = Bukkit.getPlayer(targetId);
        if (owner == null || target == null) {
            finish();
            return;
        }

        Location ownerLoc = owner.getLocation();
        Location targetLoc = target.getLocation();
        if (!ownerLoc.getWorld().equals(targetLoc.getWorld())) {
            owner.teleport(targetLoc);
        } else if (ownerLoc.distance(targetLoc) > MAX_TETHER_DISTANCE) {
            owner.teleport(targetLoc);
        }
    }

    private void finish() {
        if (finished) {
            cancelTasks();
            return;
        }
        finished = true;
        cancelTasks();
        events.unsubscribeAll(ownerId);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            restore(owner);
        }
        // Якщо офлайн — PlayerJoin-хендлер відновить при перезаході.
    }

    private void restore(Player owner) {
        try {
            owner.setGameMode(originalMode);
            if (originalLoc.getWorld() != null) {
                owner.teleport(originalLoc);
            }

            owner.playSound(owner.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1f, 1.5f);

            Location at = (originalLoc.getWorld() != null) ? originalLoc : owner.getLocation();
            if (at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 30, 0.5, 0.5, 0.5);
            }

            owner.sendMessage(ChatColor.GREEN + "✓ Ви повернулись із сну");
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Failed to end DreamVision for " + ownerId + ": " + ex.getMessage());
        }
    }

    private void cancelTasks() {
        if (trackingTask != null && !trackingTask.isCancelled()) trackingTask.cancel();
        if (endTask != null && !endTask.isCancelled()) endTask.cancel();
    }
}
