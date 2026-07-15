package me.vangoo.infrastructure.organizations;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.function.Supplier;

/**
 * Діалог священика перед дуеллю ініціації: репліки друкуються по літері в
 * action bar. Самотіковий об'єкт (власний BukkitTask); onComplete кличеться в
 * головному потоці по завершенні. Патерн {@code OrganizerBriefing}.
 */
public class DuelBriefing {

    private static final int CHAR_TICKS = 2;
    private static final int LINE_HOLD_TICKS = 35;

    private static final List<String> SCRIPT = List.of(
            "Отже, ти прагнеш ступити на шлях нашого бога.",
            "Віра доводиться не словом, а ділом.",
            "Перед тобою постане потойбічне створіння дев'ятої послідовності.",
            "Здолай його — і шлях відкриється тобі.",
            "Впадеш — повернешся живим, та з порожніми руками.",
            "Готуйся. Випробування починається."
    );

    private final Plugin plugin;
    private final Supplier<List<Player>> audience;
    private final Runnable onComplete;

    private BukkitTask task;
    private int lineIndex;
    private int charIndex;
    private int holdTicks;

    public DuelBriefing(Plugin plugin, Supplier<List<Player>> audience, Runnable onComplete) {
        this.plugin = plugin;
        this.audience = audience;
        this.onComplete = onComplete;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, CHAR_TICKS);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (lineIndex >= SCRIPT.size()) {
            finish();
            return;
        }
        String line = SCRIPT.get(lineIndex);
        String prefix = ChatColor.GOLD + "" + ChatColor.ITALIC + "Священик: " + ChatColor.WHITE;
        if (charIndex < line.length()) {
            charIndex++;
            char revealed = line.charAt(charIndex - 1);
            String shown = prefix + line.substring(0, charIndex);
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
                if (revealed != ' ') {
                    p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.3f,
                            1.2f + (float) (Math.random() * 0.3));
                }
            }
        } else if (holdTicks < LINE_HOLD_TICKS) {
            holdTicks++;
            String shown = prefix + line;
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
            }
        } else {
            lineIndex++;
            charIndex = 0;
            holdTicks = 0;
        }
    }

    private void finish() {
        cancel();
        onComplete.run();
    }
}
