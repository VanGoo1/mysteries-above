package me.vangoo.infrastructure.market;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Світ-заглушка для зборів: порожній void-світ із кам'яною залою 16×16 на y=64.
 * Інтерфейс (venueSpawn) дозволяє згодом замінити заглушку на справжню структуру,
 * не чіпаючи GatheringService.
 */
public class GatheringVenueProvider {

    public static final String WORLD_NAME = "mysteries_gathering";
    private static final int PLATFORM_Y = 64;
    private static final int HALF = 8;
    private static final int WALL_HEIGHT = 5;

    public Location venueSpawn() {
        World world = getOrCreateWorld();
        return new Location(world, 0.5, PLATFORM_Y + 1, 0.5);
    }

    public Location organizerSpawn() {
        World world = getOrCreateWorld();
        VenueLayout.Spot s = VenueLayout.organizer();
        return new Location(world, s.x(), PLATFORM_Y + 1, s.z(), s.yaw(), 0f);
    }

    public Location attendeeSpawn(int index, int total) {
        World world = getOrCreateWorld();
        VenueLayout.Spot s = VenueLayout.attendee(index, total);
        return new Location(world, s.x(), PLATFORM_Y + 1, s.z(), s.yaw(), 0f);
    }

    public boolean isVenueWorld(World world) {
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
        world.setTime(18000L); // вічна північ — атмосфера таємного збору
        buildPlatformIfMissing(world);
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);
        return world;
    }

    private void buildPlatformIfMissing(World world) {
        if (world.getBlockAt(0, PLATFORM_Y, 0).getType() == Material.POLISHED_BLACKSTONE) {
            return; // зала вже збудована
        }
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.POLISHED_BLACKSTONE);
            }
        }
        // Ліхтарі по кутах — мінімальне освітлення
        for (int[] corner : new int[][]{{-HALF, -HALF}, {-HALF, HALF}, {HALF, -HALF}, {HALF, HALF}}) {
            world.getBlockAt(corner[0], PLATFORM_Y + 1, corner[1]).setType(Material.SOUL_LANTERN);
        }
        // Кафедра ринку: правий клік відкриває меню (обробляє GatheringListener)
        world.getBlockAt(1, PLATFORM_Y + 1, 0).setType(Material.LECTERN);
        buildBarrierWalls(world);
    }

    /** Невидимі непробивні стіни по периметру платформи — щоб не випасти у порожнечу. */
    private void buildBarrierWalls(World world) {
        int minX = -HALF - 1;
        int maxX = HALF + 1;
        int minZ = -HALF - 1;
        int maxZ = HALF + 1;
        for (int y = PLATFORM_Y; y < PLATFORM_Y + WALL_HEIGHT; y++) {
            for (int x = minX; x <= maxX; x++) {
                world.getBlockAt(x, y, minZ).setType(Material.BARRIER);
                world.getBlockAt(x, y, maxZ).setType(Material.BARRIER);
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(minX, y, z).setType(Material.BARRIER);
                world.getBlockAt(maxX, y, z).setType(Material.BARRIER);
            }
        }
    }

    /** Порожній генератор: жодних чанків, лише void. */
    private static final class EmptyChunkGenerator extends ChunkGenerator {
        // Нове API ChunkGenerator: дефолтні no-op методи вже генерують порожнечу.
    }
}