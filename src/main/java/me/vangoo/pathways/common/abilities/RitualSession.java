package me.vangoo.pathways.common.abilities;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Живий хід ритуалу: заклинання по літерах в action bar над вівтарем.
 * Самотіковий об'єкт (патерн DuelBriefing/сесій): володіє своїм BukkitTask,
 * tick() перечитує гравця через Bukkit — жодного захопленого контексту.
 * Зрив: вихід за 4 бл від вівтаря, отриманий урон, смерть, офлайн.
 */
public class RitualSession {

    private static final int TICKS_PER_CHAR = 2;
    private static final int LINE_HOLD_TICKS = 20;
    private static final double MAX_DISTANCE = 4.0;

    private final UUID casterId;
    private final Location altarCenter;
    private final List<String> lines;
    private final Runnable onComplete;
    private final Consumer<String> onAbort;

    private BukkitTask task;
    private int tickCounter;
    private int lineIndex;
    private int charIndex;
    private int holdRemaining;
    private double lastHealth = -1;
    private boolean done;

    public RitualSession(UUID casterId, Location altarCenter, List<String> lines,
                         Runnable onComplete, Consumer<String> onAbort) {
        this.casterId = casterId;
        this.altarCenter = altarCenter.clone();
        this.lines = List.copyOf(lines);
        this.onComplete = onComplete;
        this.onAbort = onAbort;
    }

    public void bindTask(BukkitTask task) {
        this.task = task;
    }

    public void tick() {
        if (done) return;

        Player player = Bukkit.getPlayer(casterId);
        if (player == null || !player.isOnline() || player.isDead()) {
            abort("ритуал обірвано");
            return;
        }
        if (!player.getWorld().equals(altarCenter.getWorld())
                || player.getLocation().distance(altarCenter) > MAX_DISTANCE) {
            abort("ви полишили вівтар");
            return;
        }
        double health = player.getHealth();
        if (lastHealth >= 0 && health < lastHealth - 0.01) {
            abort("вас поранено під час заклинання");
            return;
        }
        lastHealth = health;

        tickCounter++;

        if (holdRemaining > 0) {
            holdRemaining--;
            if (holdRemaining == 0) {
                lineIndex++;
                charIndex = 0;
                if (lineIndex >= lines.size()) {
                    finishSuccess(player);
                }
            }
            return;
        }

        if (tickCounter % TICKS_PER_CHAR != 0) return;

        String line = lines.get(lineIndex);
        charIndex = Math.min(charIndex + 1, line.length());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.LIGHT_PURPLE + line.substring(0, charIndex)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.6f);
        player.getWorld().spawnParticle(Particle.ENCHANT,
                altarCenter.clone().add(0, 1.2, 0), 3, 0.6, 0.4, 0.6);

        if (charIndex >= line.length()) {
            holdRemaining = LINE_HOLD_TICKS;
        }
    }

    private void finishSuccess(Player player) {
        done = true;
        cancel();
        player.getWorld().spawnParticle(Particle.END_ROD,
                altarCenter.clone().add(0, 1.5, 0), 40, 0.8, 0.8, 0.8);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
        onComplete.run();
    }

    private void abort(String reason) {
        done = true;
        cancel();
        onAbort.accept(reason);
    }

    public void cancel() {
        if (task != null) task.cancel();
    }
}
