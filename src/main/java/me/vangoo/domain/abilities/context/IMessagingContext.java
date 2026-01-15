package me.vangoo.domain.abilities.context;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface IMessagingContext {
    void sendMessage(UUID playerId, String message);

    void sendMessageToActionBar(UUID playerId, Component message);

    void spawnTemporaryHologram(Location location, Component text, long durationTicks);

    void spawnFollowingHologramForPlayer(Player viewer, Player target, Component text, long durationTicks, long updateIntervalTicks);
}
