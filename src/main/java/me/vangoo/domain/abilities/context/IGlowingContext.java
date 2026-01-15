package me.vangoo.domain.abilities.context;

import org.bukkit.ChatColor;
import java.util.List;
import java.util.UUID;

public interface IGlowingContext {
    void setGlowing(UUID targetId, UUID casterId, ChatColor color, int durationTicks);

    void setGlowingPermanent(UUID entityId, ChatColor color);

    void removeGlowing(UUID casterId, UUID entity);

    void setMultipleGlowing(List<UUID> entitiesId, ChatColor color, int durationTicks);
}
