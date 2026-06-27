package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/** Per-entity бойова поведінка істоти. Тікається CreatureBehaviorManager раз на секунду,
 * лише коли поряд є потойбічні (передаються у nearbyBeyonders). */
public interface CreatureBehavior {
    void tick(LivingEntity self, List<Player> nearbyBeyonders);
}
