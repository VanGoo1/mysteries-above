package me.vangoo.infrastructure.market;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Доповідь Посередника на відкритті збору: репліки друкуються по літері в
 * action bar усіх учасників зі звуком друкарської машинки. Самотіковий об'єкт —
 * володіє власним BukkitTask; onComplete кличеться в головному потоці по завершенні.
 */
public class OrganizerBriefing {

    private static final int CHAR_TICKS = 2;      // тіків на символ
    private static final int LINE_HOLD_TICKS = 40; // утримання повного рядка

    // Сценарій: де ви → правила → принципи торгівлі
    private static final List<String> SCRIPT = List.of(
            "Ласкаво прошу в місце, якого немає на жодній мапі.",
            "Тут ніхто не має імені. Вас бачать лише як Незнайомця.",
            "Правила прості: тут не місце насильству.",
            "Здійняти руку чи силу на іншого — виженуть, а тоді й не пустять.",
            "Ваші здібності тут мовчать. Не намагайтеся їх пробудити.",
            "Торгуйте так: виставте річ із руки як лот за свою ціну.",
            "Або замовте потрібне — і продавці дадуть свої пропозиції.",
            "Непотріб принесіть мені, Посереднику — скуплю за монету.",
            "З кожної угоди я беру свою частку. Така ціна безпеки.",
            "Кафедра поруч відкриє вам торгове меню. Успіху."
    );

    private final Plugin plugin;
    private final Supplier<List<Player>> audience;
    private final Runnable onComplete;

    private BukkitTask task;
    private int lineIndex;
    private int charIndex;
    private int holdTicks;

    public OrganizerBriefing(Plugin plugin, Supplier<List<Player>> audience, Runnable onComplete) {
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
        if (charIndex < line.length()) {
            charIndex++;
            char revealed = line.charAt(charIndex - 1);
            String shown = ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: " + ChatColor.WHITE + line.substring(0, charIndex);
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
                if (revealed != ' ') {
                    p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.3f,
                            1.4f + (float) (Math.random() * 0.3));
                }
            }
        } else if (holdTicks < LINE_HOLD_TICKS) {
            // Утримуємо повний рядок, перепосилаючи action bar щоб не згас
            holdTicks++;
            String shown = ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: " + ChatColor.WHITE + line;
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
