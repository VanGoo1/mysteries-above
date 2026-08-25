package me.vangoo.domain.abilities.context;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface IMessagingContext {
    void sendMessage(UUID playerId, String message);

    void sendMessageToActionBar(UUID playerId, Component message);

    /**
     * Той самий actionbar, але звичайним рядком із {@code §}-кодами. Існує, щоб ядро
     * {@code abilities.core} лишалось без залежності від Adventure — реалізація й так
     * зводить {@link Component} до legacy-рядка.
     */
    void sendMessageToActionBar(UUID playerId, String message);

    void spawnTemporaryHologram(Location location, Component text, long durationTicks);

    void spawnFollowingHologramForPlayer(Player viewer, Player target, Component text, long durationTicks, long updateIntervalTicks);
}
