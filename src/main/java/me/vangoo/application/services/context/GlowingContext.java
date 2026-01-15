package me.vangoo.application.services.context;

import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.domain.abilities.context.IGlowingContext;
import me.vangoo.domain.abilities.context.ISchedulingContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class GlowingContext implements IGlowingContext {

    private final GlowingEntities glowingEntities;
    private final Plugin plugin; // Замінили ISchedulingContext на Plugin
    private final Logger logger;

    // В конструктор тепер передаємо головний клас плагіна
    public GlowingContext(GlowingEntities glowingEntities, Plugin plugin, Logger logger) {
        this.glowingEntities = glowingEntities;
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public void setGlowing(UUID targetId, UUID casterId, ChatColor color, int durationTicks) {
        Player caster = Bukkit.getPlayer(casterId);
        Entity target = Bukkit.getEntity(targetId);

        if (target == null || caster == null) return;

        try {
            glowingEntities.setGlowing(target, caster, color);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeGlowing(casterId, targetId);
            }, durationTicks);

        } catch (ReflectiveOperationException e) {
            logger.warning("Failed to set glowing for entity " + targetId + ": " + e.getMessage());
        }
    }

    @Override
    public void setGlowingPermanent(UUID entityId, ChatColor color) {
        Entity target = Bukkit.getEntity(entityId);
        if (target == null) return;

        // Оскільки в інтерфейсі немає casterId, вмикаємо світіння для ВСІХ гравців на сервері
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                glowingEntities.setGlowing(target, player, color);
            } catch (ReflectiveOperationException e) {
                logger.warning("Failed to set permanent glowing for entity " + entityId + " for player " + player.getName());
            }
        }
    }

    @Override
    public void removeGlowing(UUID casterId, UUID entityId) {
        Player caster = Bukkit.getPlayer(casterId);
        Entity target = Bukkit.getEntity(entityId);
        if (caster == null) return;
        if (target == null) return;

        try {
            glowingEntities.unsetGlowing(target, caster);
        } catch (ReflectiveOperationException e) {
            logger.warning("Failed to remove glowing from entity " + entityId + ": " + e.getMessage());
        }
    }

    @Override
    public void setMultipleGlowing(List<UUID> entitiesId, ChatColor color, int durationTicks) {
        for (UUID entityId : entitiesId) {
            Entity target = Bukkit.getEntity(entityId);
            if (target == null) continue;

            for (Player player : Bukkit.getOnlinePlayers()) {
                setGlowing(entityId, player.getUniqueId(), color, durationTicks);
            }
        }
    }
}