package me.vangoo.presentation.listeners;

import me.vangoo.domain.market.PoundMoney;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Одноразовий чат-промпт (ціна, кількість). LOWEST: забирає відповідь ДО того,
 * як GatheringListener (NORMAL) перетворить її на анонімне повідомлення зали.
 * Промпт має обмежений час життя (TTL_MILLIS): якщо гравець не відповів вчасно,
 * прострочений запис НЕ ковтає наступний (сторонній) рядок чату; він також знімається
 * при виході гравця — щоб стале очікування не викликало помилкову дію.
 */
public class ChatPromptService implements Listener {

    /** Час життя невідповідженого промпту: одна хвилина — достатньо, щоб набрати ціну/кількість. */
    static final long TTL_MILLIS = 60_000L;

    record Pending(Consumer<String> handler, long deadlineMillis) {}

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPromptService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String instruction, Consumer<String> handler) {
        // Останній промпт перемагає: заміна записує новий дедлайн.
        pending.put(player.getUniqueId(),
                new Pending(handler, System.currentTimeMillis() + TTL_MILLIS));
        player.closeInventory();
        player.sendMessage(instruction);
        player.sendMessage(ChatColor.DARK_GRAY + "(напишіть «скасувати», щоб відмовитись)");
    }

    /**
     * LOWEST: async-потік. Тут робимо лише потокобезпечне — знімаємо очікуваний хендлер
     * і скасовуємо подію; сам handler (може мутувати GatheringService/Beyonder) виконуємо
     * в головному потоці через Bukkit.getScheduler().runTask. Прострочений/відсутній запис
     * НЕ ковтає повідомлення — воно йде в чат як звичайне.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending entry = pending.remove(event.getPlayer().getUniqueId());
        if (!isLive(entry, System.currentTimeMillis())) {
            return; // немає активного промпту (нема запису або прострочено) — пропускаємо в чат
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Player player = event.getPlayer();
        Consumer<String> handler = entry.handler();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("скасувати")) {
                player.sendMessage(ChatColor.GRAY + "Скасовано.");
                return;
            }
            handler.accept(message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    /** Живий промпт — наявний запис, дедлайн якого ще не минув. Чиста логіка (тестується без Bukkit). */
    static boolean isLive(Pending entry, long nowMillis) {
        return entry != null && nowMillis <= entry.deadlineMillis();
    }

    /** «2 15» → 2 ф 15 к; «7» → 7 ф. Невалідний ввід → null. */
    public static PoundMoney parsePrice(String input) {
        try {
            String[] parts = input.trim().split("\\s+");
            int pounds = Integer.parseInt(parts[0]);
            int coppets = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (pounds < 0 || coppets < 0 || parts.length > 2) {
                return null;
            }
            return PoundMoney.of(pounds, coppets);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
