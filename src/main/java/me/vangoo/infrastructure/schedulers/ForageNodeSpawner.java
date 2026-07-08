package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.forage.ForageSelector;
import me.vangoo.infrastructure.forage.ForageConfig;
import me.vangoo.infrastructure.forage.ForageNode;
import me.vangoo.infrastructure.forage.ForageNodeCodec;
import me.vangoo.infrastructure.forage.ForageNodeLocation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Ambient-спавн «зачарованих» блоків фореджу: періодично для кожного онлайн-гравця з шансом
 * підміняє найближчу вегетацію/листя на блок-донор (ресурспак малює його зачарованим) і
 * пам'ятає оригінал для відновлення (TTL/stop/креш через PDC чанка). Без дистанційного
 * гейту — форедж усюди. Інваріант: живі ноди існують лише в завантажених чанках.
 */
public final class ForageNodeSpawner {

    private static final double NEARBY_RADIUS_SQ = 32.0 * 32.0;

    private final MysteriesAbovePlugin plugin;
    private final ForageSelector selector;
    private final ForageNodeCodec codec;
    private final ForageConfig config;
    private final Random random = new Random();
    /** Живі ноди за позицією блока (ключ — key(block)). */
    private final Map<String, ForageNode> nodes = new HashMap<>();
    private final long intervalTicks;
    private final long ttlMillis;

    private BukkitTask spawnTask;
    private BukkitTask particleTask;

    public ForageNodeSpawner(MysteriesAbovePlugin plugin, ForageSelector selector,
                             ForageNodeCodec codec, ForageConfig config) {
        this.plugin = plugin;
        this.selector = selector;
        this.codec = codec;
        this.config = config;
        this.intervalTicks = Math.max(20L, config.intervalSeconds() * 20L);
        this.ttlMillis = Math.max(1000L, config.ttlSeconds() * 1000L);
    }

    public void start() {
        if (spawnTask != null && !spawnTask.isCancelled()) return;
        healAllLoadedChunks();
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        long particlePeriod = Math.max(10L, config.particlePeriodTicks());
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particleTick, particlePeriod, particlePeriod);
        plugin.getLogger().info("ForageNodeSpawner started");
    }

    public void stop() {
        if (spawnTask != null) { spawnTask.cancel(); spawnTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        for (ForageNode n : List.copyOf(nodes.values())) restoreAndRemove(n);
        plugin.getLogger().info("ForageNodeSpawner stopped");
    }

    /** Жива нода на цьому блоці. */
    public Optional<ForageNode> nodeAt(Block block) {
        return Optional.ofNullable(nodes.get(key(block)));
    }

    /** Збір гравцем: блок уже ламає подія — лише зняти з реєстру та PDC чанка. */
    public void onHarvested(ForageNode node) {
        deregister(node);
    }

    /** Дострокове/нештатне зняття: повернути оригінальний блок і зняти ноду. */
    public void restoreAndRemove(ForageNode node) {
        node.restore();
        deregister(node);
    }

    public List<ForageNode> nodesInChunk(Chunk chunk) {
        List<ForageNode> result = new ArrayList<>();
        for (ForageNode n : nodes.values()) {
            Block b = n.getBlock();
            if (b.getWorld().equals(chunk.getWorld())
                    && (b.getX() >> 4) == chunk.getX() && (b.getZ() >> 4) == chunk.getZ()) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * Відновити «осиротілі» записи PDC чанка (лишились після крешу). Викликати лише коли
     * в чанку немає живих нод (завантаження чанка; старт плагіна).
     */
    public void healChunk(Chunk chunk) {
        List<ForageNodeCodec.StoredNode> stored = codec.read(chunk);
        if (stored.isEmpty()) return;
        World world = chunk.getWorld();
        for (ForageNodeCodec.StoredNode s : stored) {
            try {
                Block b = world.getBlockAt(s.x(), s.y(), s.z());
                b.setBlockData(Bukkit.createBlockData(s.lowerData()), false);
                if (s.upperData() != null) {
                    b.getRelative(BlockFace.UP).setBlockData(Bukkit.createBlockData(s.upperData()), false);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Forage heal: bad block data in chunk PDC: " + e.getMessage());
            }
        }
        codec.clear(chunk);
        plugin.getLogger().info("Forage: healed " + stored.size() + " stale node(s) in chunk "
                + chunk.getX() + "," + chunk.getZ());
    }

    private void healAllLoadedChunks() {
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) healChunk(c);
        }
    }

    private void tick() {
        prune();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                trySpawnFor(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Forage spawn error for " + player.getName() + ": " + e);
            }
        }
    }

    private void particleTick() {
        for (ForageNode n : nodes.values()) {
            n.getBlock().getWorld().spawnParticle(
                    Particle.GLOW, n.particleLocation(), 2, 0.25, 0.25, 0.25, 0.0);
        }
    }

    private void prune() {
        for (ForageNode n : List.copyOf(nodes.values())) {
            if (!n.isIntact()) {
                deregister(n); // блок знесено повз лістенер — не воскрешаємо рослину з повітря
            } else if (n.ageMillis() > ttlMillis) {
                restoreAndRemove(n); // не зібрали — тихо повертаємо оригінал
            }
        }
    }

    private void trySpawnFor(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) return;
        if (random.nextDouble() >= config.chance()) return;
        if (countNear(player) >= config.maxNearby()) return;

        Optional<Block> target = findTarget(player);
        if (target.isEmpty()) return;
        Block block = target.get();
        if (nodes.containsKey(key(block))) return;

        boolean isLeaves = config.leaves().contains(block.getType());
        Material donor = Material.matchMaterial(
                config.donors().donorFor(block.getType().name(), isLeaves));
        if (donor == null) return; // донори валідує лоадер; це страховка

        Optional<String> pick = selector.pickForBiome(block.getBiome().name(), random.nextDouble());
        if (pick.isEmpty()) return;

        register(ForageNode.place(block, donor, pick.get()));
    }

    private Optional<Block> findTarget(Player player) {
        boolean leavesFirst = !config.leaves().isEmpty() && random.nextBoolean();
        if (leavesFirst) {
            Optional<Block> leaves = ForageNodeLocation.findLeavesNear(
                    player, config.leaves(), config.searchRadius());
            if (leaves.isPresent()) return leaves;
            return ForageNodeLocation.findVegetationNear(player, config.vegetation(), config.searchRadius());
        }
        Optional<Block> flora = ForageNodeLocation.findVegetationNear(
                player, config.vegetation(), config.searchRadius());
        if (flora.isPresent()) return flora;
        return ForageNodeLocation.findLeavesNear(player, config.leaves(), config.searchRadius());
    }

    private int countNear(Player player) {
        int count = 0;
        for (ForageNode n : nodes.values()) {
            Block b = n.getBlock();
            if (b.getWorld().equals(player.getWorld())
                    && b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(player.getLocation()) <= NEARBY_RADIUS_SQ) {
                count++;
            }
        }
        return count;
    }

    private void register(ForageNode node) {
        Block b = node.getBlock();
        nodes.put(key(b), node);
        codec.add(b.getChunk(), new ForageNodeCodec.StoredNode(
                b.getX(), b.getY(), b.getZ(),
                node.originalLower().getAsString(),
                node.originalUpper() == null ? null : node.originalUpper().getAsString()));
    }

    private void deregister(ForageNode node) {
        Block b = node.getBlock();
        nodes.remove(key(b));
        codec.remove(b.getChunk(), b.getX(), b.getY(), b.getZ());
    }

    private static String key(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }
}
