package me.vangoo.application.services.context;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IMessagingContext;
import me.vangoo.domain.abilities.context.ISchedulingContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.UUID;

public class MessagingContext implements IMessagingContext {

    private final MysteriesAbovePlugin plugin;
    private final ISchedulingContext schedulingContext;

    public MessagingContext(MysteriesAbovePlugin plugin, ISchedulingContext schedulingContext) {
        this.plugin = plugin;
        this.schedulingContext = schedulingContext;
    }

    @Override
    public void sendMessage(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void sendMessageToActionBar(UUID playerId, Component message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            String legacy = LegacyComponentSerializer.legacySection().serialize(message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(legacy));
        }
    }

    @Override
    public void spawnTemporaryHologram(Location location, Component text, long durationTicks) {
        ArmorStand holo = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        holo.setVisible(false);
        holo.setGravity(false);
        holo.setMarker(true);
        holo.setCustomNameVisible(true);
        holo.setInvulnerable(true);

        String serializedText = LegacyComponentSerializer.legacySection().serialize(text);
        holo.setCustomName(serializedText);

        schedulingContext.scheduleDelayed(() -> {
            if (holo.isValid()) {
                holo.remove();
            }
        }, durationTicks);
    }

    @Override
    public void spawnFollowingHologramForPlayer(Player viewer, Player target, Component text, long durationTicks, long updateIntervalTicks) {
        if (viewer == null || !viewer.isOnline() || target == null || !target.isOnline()) return;

        Location start = target.getLocation().clone().add(0, target.getEyeHeight() + 0.5, 0);
        ArmorStand holo = (ArmorStand) start.getWorld().spawnEntity(start, EntityType.ARMOR_STAND);
        holo.setVisible(false);
        holo.setGravity(false);
        holo.setMarker(true);
        holo.setCustomNameVisible(true);
        holo.setInvulnerable(true);

        String serialized = LegacyComponentSerializer.legacySection().serialize(text);
        holo.setCustomName(serialized);

        for (Player p : target.getWorld().getPlayers()) {
            if (!p.equals(viewer)) p.hideEntity(plugin, holo);
        }

        final long[] elapsed = {0};

        schedulingContext.scheduleRepeating(() -> {
            if (!holo.isValid() || !target.isOnline() || target.isDead() || !viewer.isOnline()) {
                if (holo.isValid()) holo.remove();
                return;
            }

            holo.teleport(target.getLocation().clone().add(0, target.getEyeHeight() + 0.5, 0));

            elapsed[0] += updateIntervalTicks;
            if (elapsed[0] >= durationTicks) {
                if (holo.isValid()) holo.remove();
            }
        }, 0L, updateIntervalTicks);

        schedulingContext.scheduleDelayed(() -> {
            if (holo.isValid()) holo.remove();
        }, durationTicks);
    }
}
