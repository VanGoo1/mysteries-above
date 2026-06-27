package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

import java.util.Optional;
import java.util.Random;

/**
 * Спавнить apex-істот біля кастомних/ванільних структур, реагуючи на генерацію їхнього луту
 * (LootGenerateEvent). Окремий лістенер: спавн ІСТОТИ — не генерація предмета (тому не в
 * VanillaStructureLootListener).
 */
public class StructureCreatureSpawnListener implements Listener {

    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final Random random = new Random();

    public StructureCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner) {
        this.selector = selector;
        this.spawner = spawner;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        Location loc = event.getLootContext().getLocation();
        if (loc == null || loc.getWorld() == null) return;

        String key = event.getLootTable().getKey().toString();
        Optional<CreatureDefinition> pick = selector.pickForStructure(key, random.nextDouble());
        if (pick.isEmpty()) return;

        spawner.spawn(pick.get(), loc);
    }
}
