package me.vangoo.application.services;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;
import java.util.UUID;

/**
 * Живий стан одного рейду на сховище храму: самотіковий об'єкт із власним
 * {@link BukkitTask} (тік що секунду), за зразком {@link DuelSession} +
 * {@code OrganizerBriefing}. НІКОЛИ не static — реєстр {@code Map<UUID,RaidSession>}
 * живе в {@link SecretOrderService}. Тік не тримає жодного захопленого контексту —
 * гравця перечитує з {@link Bukkit#getPlayer(UUID)} щоразу.
 *
 * <p>Кожен тік (20 тіків): гравець офлайн/мертвий/поза {@code zoneRadius} від центру
 * сайту → {@code onFail}+cancel; інакше секунда злому++; поки {@code !alarmed} — одна
 * перевірка тривоги ({@code random < alarmChancePerSecond}); коли злом досяг
 * {@code channelSeconds} → {@code onSuccess}+cancel.
 */
public class RaidSession {

    private final UUID playerId;
    private final String churchId;
    private final Location siteCenter;
    private final int channelSeconds;
    private final double alarmChancePerSecond;
    private final int zoneRadius;
    private final Runnable onAlarm;
    private final Runnable onSuccess;
    private final Runnable onFail;

    private BukkitTask task;
    private Random random;
    private int progress;
    private boolean alarmed;
    private boolean finished;

    public RaidSession(UUID playerId, String churchId, Location siteCenter,
                       int channelSeconds, double alarmChancePerSecond, int zoneRadius,
                       Runnable onAlarm, Runnable onSuccess, Runnable onFail) {
        this.playerId = playerId;
        this.churchId = churchId;
        this.siteCenter = siteCenter;
        this.channelSeconds = channelSeconds;
        this.alarmChancePerSecond = alarmChancePerSecond;
        this.zoneRadius = zoneRadius;
        this.onAlarm = onAlarm;
        this.onSuccess = onSuccess;
        this.onFail = onFail;
    }

    public void start(org.bukkit.plugin.Plugin plugin, Random random) {
        this.random = random;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (finished) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead() || outOfZone(player)) {
            finish(onFail);
            return;
        }
        progress++;
        if (!alarmed && random.nextDouble() < alarmChancePerSecond) {
            alarmed = true;
            onAlarm.run();
        }
        if (progress >= channelSeconds) {
            finish(onSuccess);
            return;
        }
        sendActionBar(player);
    }

    private void sendActionBar(Player player) {
        String text = ChatColor.DARK_RED + "⚒ Злом... " + ChatColor.RED + progress + "/" + channelSeconds
                + (alarmed ? ChatColor.GOLD + "  ⚠ Тривога!" : "");
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    private boolean outOfZone(Player player) {
        Location loc = player.getLocation();
        if (siteCenter.getWorld() == null || loc.getWorld() == null
                || !siteCenter.getWorld().equals(loc.getWorld())) {
            return true;
        }
        return loc.distance(siteCenter) > zoneRadius;
    }

    private void finish(Runnable outcome) {
        if (finished) {
            return;
        }
        finished = true;
        cancel();
        outcome.run();
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean alarmed() {
        return alarmed;
    }

    public UUID playerId() {
        return playerId;
    }

    public String churchId() {
        return churchId;
    }
}
