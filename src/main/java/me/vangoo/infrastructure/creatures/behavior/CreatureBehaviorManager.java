package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Центральний тік-менеджер поведінок істот: один повторюваний таск ітерує живі тегувані істоти
 * і делегує до їхнього CreatureBehavior, коли поряд є потойбічні. Само-очищає мертвих/відсутніх. */
public final class CreatureBehaviorManager {

    private static final double R = 12.0;

    private final BeyonderService beyonderService;
    private final CreatureBehaviorFactory factory;
    private final Map<UUID, CreatureBehavior> behaviors = new HashMap<>();
    private final BukkitTask task;

    public CreatureBehaviorManager(Plugin plugin, BeyonderService beyonderService, CreatureBehaviorFactory factory) {
        this.beyonderService = beyonderService;
        this.factory = factory;
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 20L);
    }

    public void start(LivingEntity entity, CreatureDefinition def) {
        CreatureBehavior behavior = factory.create(def);
        if (behavior != null) {
            behaviors.put(entity.getUniqueId(), behavior);
        }
    }

    public void stopAll() {
        if (task != null) {
            task.cancel();
        }
        behaviors.clear();
    }

    private void tickAll() {
        if (behaviors.isEmpty()) return;
        for (Map.Entry<UUID, CreatureBehavior> e : new ArrayList<>(behaviors.entrySet())) {
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity self) || self.isDead()) {
                behaviors.remove(e.getKey());
                continue;
            }
            List<Player> nearby = new ArrayList<>();
            for (Entity n : self.getNearbyEntities(R, R, R)) {
                if (n instanceof Player p && beyonderService.getBeyonder(p.getUniqueId()) != null) {
                    nearby.add(p);
                }
            }
            if (nearby.isEmpty()) continue;
            e.getValue().tick(self, nearby);
        }
    }
}
