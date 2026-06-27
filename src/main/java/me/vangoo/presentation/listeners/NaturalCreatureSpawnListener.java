package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Optional;
import java.util.Random;

/**
 * Підвищує природний (NATURAL) спавн ванільного моба у кастомну істоту, якщо біом+тип збігаються з
 * правилом істоти. Заміна-спавн іде з reason CUSTOM, тож рекурсії немає.
 */
public class NaturalCreatureSpawnListener implements Listener {

    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final Random random = new Random();

    public NaturalCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner) {
        this.selector = selector;
        this.spawner = spawner;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        String biome = event.getLocation().getBlock().getBiome().name();
        String type = event.getEntityType().name();

        Optional<CreatureDefinition> pick = selector.pickForBiome(biome, type, random.nextDouble());
        if (pick.isEmpty()) return;

        event.setCancelled(true);
        spawner.spawn(pick.get(), event.getLocation());
    }
}
