package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.ConvergenceBias;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.domain.creatures.SpawnDistanceGate;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.creatures.AmbientSpawnLocation;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Ambient-спавн міфічних істот: періодично, для кожного потойбічного далеко від спавну світу, з
 * малим шансом підкидає поряд істоту ЙОГО шляху+послідовності (закон конвергенції) — нікого не
 * заміняючи. Дзеркалить життєвий цикл інших планувальників (start/stop + один BukkitTask).
 */
public final class AmbientCreatureSpawner {

    private static final double SPAWN_MIN_R = 24.0;
    private static final double SPAWN_MAX_R = 48.0;
    private static final double NEARBY_RADIUS = 64.0;

    private final MysteriesAbovePlugin plugin;
    private final BeyonderService beyonderService;
    private final CreatureSelector selector;
    private final MythicCreatureGateway gateway;
    private final Map<String, CreatureDefinition> registry;
    private final double minSpawnDistance;
    private final long intervalTicks;
    private final double chance;
    private final int maxNearby;
    private final Random random = new Random();

    private BukkitTask task;

    public AmbientCreatureSpawner(MysteriesAbovePlugin plugin, BeyonderService beyonderService,
                                  CreatureSelector selector, MythicCreatureGateway gateway,
                                  Map<String, CreatureDefinition> registry,
                                  double minSpawnDistance, long intervalSeconds, double chance, int maxNearby) {
        this.plugin = plugin;
        this.beyonderService = beyonderService;
        this.selector = selector;
        this.gateway = gateway;
        this.registry = registry;
        this.minSpawnDistance = minSpawnDistance;
        this.intervalTicks = Math.max(20L, intervalSeconds * 20L);
        this.chance = chance;
        this.maxNearby = maxNearby;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("AmbientCreatureSpawner started");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
            plugin.getLogger().info("AmbientCreatureSpawner stopped");
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                trySpawnFor(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Ambient spawn error for " + player.getName() + ": " + e.toString());
            }
        }
    }

    private void trySpawnFor(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        UUID id = player.getUniqueId();
        Beyonder beyonder = beyonderService.getBeyonder(id);
        if (beyonder == null) return;

        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;
        Location ws = loc.getWorld().getSpawnLocation();
        if (!SpawnDistanceGate.isFarEnough(loc.getX() - ws.getX(), loc.getZ() - ws.getZ(), minSpawnDistance)) return;

        if (random.nextDouble() >= chance) return;

        int near = 0;
        for (Entity e : player.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
            if (gateway.creatureId(e).map(registry::containsKey).orElse(false)) near++;
        }
        if (near >= maxNearby) return;

        ConvergenceBias bias = new ConvergenceBias(beyonder.getPathway().getName(), beyonder.getSequenceLevel());
        String biome = loc.getBlock().getBiome().name();
        Optional<CreatureDefinition> pick = selector.pickForAmbient(biome, bias, random.nextDouble());
        if (pick.isEmpty()) return;

        boolean aquatic = AmbientSpawnLocation.isAquatic(pick.get().baseEntityType());
        Optional<Location> spot = AmbientSpawnLocation.findSpawnNear(loc, SPAWN_MIN_R, SPAWN_MAX_R, aquatic);
        if (spot.isEmpty()) return;

        gateway.spawn(pick.get().id(), spot.get());
    }
}
