package me.vangoo.domain.abilities.context;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public interface ITargetContext {
    List<LivingEntity> getNearbyEntities(double radius);

    List<Player> getNearbyPlayers(double radius);

    Optional<LivingEntity> getTargetedEntity(double maxRange);

    Optional<Player> getTargetedPlayer(double maxRange);
}
