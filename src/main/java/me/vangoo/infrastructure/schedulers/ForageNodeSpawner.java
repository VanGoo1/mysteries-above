package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.forage.ForageSelector;
import me.vangoo.infrastructure.forage.ForageConfig;
import me.vangoo.infrastructure.forage.ForageNode;
import me.vangoo.infrastructure.forage.ForageNodeCodec;
import me.vangoo.infrastructure.forage.ForageNodeLocation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Ambient-спавн нод фореджу: періодично для кожного онлайн-гравця з шансом підкидає на найближчу
 * вегетацію видиму ноду допоміжного інгредієнта (біом-тематично). Без дистанційного гейту —
 * форедж усюди. Дзеркалить життєвий цикл AmbientCreatureSpawner (start/stop + один BukkitTask).
 */
public final class ForageNodeSpawner {

    private static final double NEARBY_RADIUS = 32.0;

    private final MysteriesAbovePlugin plugin;
    private final ForageSelector selector;
    private final CustomItemService customItemService;
    private final ForageNodeCodec codec;
    private final ForageConfig config;
    private final Random random = new Random();
    private final List<ForageNode> nodes = new ArrayList<>();
    private final long intervalTicks;
    private final long ttlMillis;

    private BukkitTask task;

    public ForageNodeSpawner(MysteriesAbovePlugin plugin, ForageSelector selector,
                             CustomItemService customItemService, ForageNodeCodec codec, ForageConfig config) {
        this.plugin = plugin;
        this.selector = selector;
        this.customItemService = customItemService;
        this.codec = codec;
        this.config = config;
        this.intervalTicks = Math.max(20L, config.intervalSeconds() * 20L);
        this.ttlMillis = Math.max(1000L, config.ttlSeconds() * 1000L);
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("ForageNodeSpawner started");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        for (ForageNode n : nodes) n.remove();
        nodes.clear();
        plugin.getLogger().info("ForageNodeSpawner stopped");
    }

    private void tick() {
        pruneNodes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                trySpawnFor(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Forage spawn error for " + player.getName() + ": " + e);
            }
        }
    }

    private void pruneNodes() {
        nodes.removeIf(n -> {
            if (!n.isAlive() || n.ageMillis() > ttlMillis) {
                n.remove();
                return true;
            }
            return false;
        });
    }

    private void trySpawnFor(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) return;
        if (random.nextDouble() >= config.chance()) return;

        int near = 0;
        for (Entity e : player.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
            if (e instanceof Interaction && codec.isForageNode(e)) near++;
        }
        if (near >= config.maxNearby()) return;

        Optional<org.bukkit.block.Block> spot = ForageNodeLocation.findVegetationNear(player, config.vegetation(), config.searchRadius());
        if (spot.isEmpty()) return;

        String biome = spot.get().getBiome().name();
        Optional<String> pick = selector.pickForBiome(biome, random.nextDouble());
        if (pick.isEmpty()) return;

        Optional<ItemStack> model = customItemService.createItemStack(pick.get());
        if (model.isEmpty()) return;

        nodes.add(ForageNode.spawn(spot.get().getLocation().add(0.5, 0.5, 0.5), model.get(), pick.get(), codec));
    }
}
