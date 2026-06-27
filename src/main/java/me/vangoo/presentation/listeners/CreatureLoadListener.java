package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.infrastructure.creatures.CreatureCodec;
import me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.Map;
import java.util.Optional;

/** Повторно прив'язує поведінку до тегованих істот, коли вони завантажуються (chunk load / рестарт),
 * читаючи їхній CreatureCodec-тег. Без цього істота втрачала б поведінку після вивантаження чанка. */
public class CreatureLoadListener implements Listener {

    private final CreatureCodec codec;
    private final Map<String, CreatureDefinition> registry;
    private final CreatureBehaviorManager behaviorManager;

    public CreatureLoadListener(CreatureCodec codec,
                                Map<String, CreatureDefinition> registry,
                                CreatureBehaviorManager behaviorManager) {
        this.codec = codec;
        this.registry = registry;
        this.behaviorManager = behaviorManager;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            Optional<String> id = codec.readId(living);
            if (id.isEmpty()) continue;
            CreatureDefinition def = registry.get(id.get());
            if (def != null) {
                behaviorManager.start(living, def);
            }
        }
    }
}
