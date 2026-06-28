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
    private final double minSpawnDistance;
    private final me.vangoo.application.services.BeyonderService beyonderService;
    private final Random random = new Random();

    public NaturalCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner,
                                        double minSpawnDistance,
                                        me.vangoo.application.services.BeyonderService beyonderService) {
        this.selector = selector;
        this.spawner = spawner;
        this.minSpawnDistance = minSpawnDistance;
        this.beyonderService = beyonderService;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        org.bukkit.Location loc = event.getLocation();
        if (loc.getWorld() != null) {
            org.bukkit.Location ws = loc.getWorld().getSpawnLocation();
            if (!me.vangoo.domain.creatures.SpawnDistanceGate.isFarEnough(
                    loc.getX() - ws.getX(), loc.getZ() - ws.getZ(), minSpawnDistance)) {
                return;
            }
        }

        String biome = event.getLocation().getBlock().getBiome().name();
        String type = event.getEntityType().name();

        me.vangoo.domain.creatures.ConvergenceBias bias = nearestBias(event.getLocation());
        Optional<CreatureDefinition> pick =
                selector.pickForBiome(biome, type, random.nextDouble(), bias);
        if (pick.isEmpty()) return;

        event.setCancelled(true);
        spawner.spawn(pick.get(), event.getLocation());
    }

    /** Знаходить найближчого онлайн-потойбічного в радіусі 48 блоків і будує з нього зсув. */
    private me.vangoo.domain.creatures.ConvergenceBias nearestBias(org.bukkit.Location loc) {
        if (loc.getWorld() == null) return null;
        org.bukkit.entity.Player nearest = null;
        double best = 48.0 * 48.0;
        for (org.bukkit.entity.Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d <= best) {
                me.vangoo.domain.entities.Beyonder b = beyonderService.getBeyonder(p.getUniqueId());
                if (b != null) {
                    best = d;
                    nearest = p;
                }
            }
        }
        if (nearest == null) return null;
        me.vangoo.domain.entities.Beyonder b = beyonderService.getBeyonder(nearest.getUniqueId());
        return new me.vangoo.domain.creatures.ConvergenceBias(
                b.getPathway().getName(), b.getSequenceLevel());
    }
}
