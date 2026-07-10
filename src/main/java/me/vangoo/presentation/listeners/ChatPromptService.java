package me.vangoo.presentation.listeners;

import me.vangoo.domain.market.PoundMoney;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Одноразовий чат-промпт (ціна, кількість). LOWEST: забирає відповідь ДО того,
 * як GatheringListener (NORMAL) перетворить її на анонімне повідомлення зали.
 */
public class ChatPromptService implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPromptService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String instruction, Consumer<String> handler) {
        pending.put(player.getUniqueId(), handler);
        player.closeInventory();
        player.sendMessage(instruction);
        player.sendMessage(ChatColor.DARK_GRAY + "(напишіть «скасувати», щоб відмовитись)");
    }

    /**
     * LOWEST: async-потік. Тут робимо лише потокобезпечне — знімаємо очікуваний хендлер
     * і скасовуємо подію; сам handler (може мутувати GatheringService/Beyonder) виконуємо
     * в головному потоці через Bukkit.getScheduler().runTask.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("скасувати")) {
                player.sendMessage(ChatColor.GRAY + "Скасовано.");
                return;
            }
            handler.accept(message);
        });
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
