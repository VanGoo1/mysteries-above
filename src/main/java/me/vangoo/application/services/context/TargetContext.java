package me.vangoo.application.services.context;

import me.vangoo.domain.abilities.context.ITargetContext;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class TargetContext implements ITargetContext {

    private final Player caster;
    private final Map<UUID, Entity> entityCache = new HashMap<>();

    public TargetContext(Player caster) {
        this.caster = caster;
    }

    @Override
    public List<LivingEntity> getNearbyEntities(double radius) {
        List<LivingEntity> result = new ArrayList<>();

        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.equals(caster)) continue;
            if (entity.isDead()) continue;

            result.add(living);
            entityCache.put(entity.getUniqueId(), entity);
        }

        return result;
    }

    @Override
    public List<Player> getNearbyPlayers(double radius) {
        List<Player> result = new ArrayList<>();

        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player player)) continue;
            if (entity.equals(caster)) continue;
            if (player.getGameMode() == GameMode.SPECTATOR) continue;

            result.add(player);
            entityCache.put(entity.getUniqueId(), entity);
        }

        return result;
    }

    @Override
    public Optional<LivingEntity> getTargetedEntity(double maxRange) {
        RayTraceResult result = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                maxRange,
                entity -> entity instanceof LivingEntity && !entity.equals(caster)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity living) {
            entityCache.put(living.getUniqueId(), living);
            return Optional.of(living);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Player> getTargetedPlayer(double maxRange) {
        RayTraceResult result = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                maxRange,
                entity -> entity instanceof Player && !entity.equals(caster)
        );

        if (result != null && result.getHitEntity() instanceof Player player) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                entityCache.put(player.getUniqueId(), player);
                return Optional.of(player);
            }
        }

        return Optional.empty();
    }
}
