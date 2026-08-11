package me.vangoo.infrastructure.compat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Рядок над хотбаром через Spigot-API.
 *
 * <p>Paper'ів {@code Player.sendActionBar(Component)} — доповнення Paper до {@code Player}
 * (через Adventure {@code Audience}), і на сервері проєкту (Arclight) його немає. Spigot-шлях
 * {@code player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ...)} є скрізь, тож усі
 * повідомлення над хотбаром ідуть сюди.
 *
 * <p>Adventure-{@code Component} лишається зручним способом ЗІБРАТИ текст (кольори, символи) —
 * тут він лише серіалізується в legacy-рядок; сам Adventure їде в джарі як shade-залежність,
 * тож від сервера не залежить.
 */
public final class ActionBars {

    private ActionBars() {
    }

    /** Adventure-компонент → legacy-рядок → Spigot action bar. */
    public static void send(Player player, Component message) {
        if (message == null) return;
        send(player, LegacyComponentSerializer.legacySection().serialize(message));
    }

    /** Готовий legacy-рядок (з {@code ChatColor}) над хотбаром. */
    public static void send(Player player, String legacyText) {
        if (player == null || !player.isOnline() || legacyText == null) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(legacyText));
    }
}
