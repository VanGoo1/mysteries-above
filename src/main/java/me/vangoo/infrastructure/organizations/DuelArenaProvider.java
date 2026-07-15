package me.vangoo.infrastructure.organizations;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Void-світ для дуелі ініціації: кам'яна арена 21×21 на y=64. Ідемпотентний,
 * за зразком {@link me.vangoo.infrastructure.market.GatheringVenueProvider}.
 */
public class DuelArenaProvider {

    public static final String WORLD_NAME = "mysteries_duel";
    private static final int PLATFORM_Y = 64;
    private static final int HALF = 10;

    public Location arenaSpawn() {
        World world = getOrCreateWorld();
        return new Location(world, 0.5, PLATFORM_Y + 1, 8.5, 180f, 0f); // гравець дивиться на північ (до опонента)
    }

    public Location opponentSpawn() {
        World world = getOrCreateWorld();
        return new Location(world, 0.5, PLATFORM_Y + 1, -6.5, 0f, 0f);
    }

    public boolean isDuelWorld(World world) {
        return world != null && WORLD_NAME.equals(world.getName());
    }

    private World getOrCreateWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            return existing;
        }
        World world = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generator(new EmptyChunkGenerator())
                .createWorld();
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(15000L);
        buildArenaIfMissing(world);
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);
        return world;
    }

    private void buildArenaIfMissing(World world) {
        if (world.getBlockAt(0, PLATFORM_Y, 0).getType() == Material.POLISHED_ANDESITE) {
            return;
        }
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.POLISHED_ANDESITE);
            }
        }
        for (int[] corner : new int[][]{{-HALF, -HALF}, {-HALF, HALF}, {HALF, -HALF}, {HALF, HALF}}) {
            world.getBlockAt(corner[0], PLATFORM_Y + 1, corner[1]).setType(Material.SOUL_LANTERN);
        }
    }

    private static final class EmptyChunkGenerator extends ChunkGenerator {
    }
}
