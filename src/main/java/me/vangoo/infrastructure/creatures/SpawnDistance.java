package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.SpawnDistanceGate;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Bukkit-обгортка над {@link SpawnDistanceGate}: міряє відстань від спавну світу з поправкою на
 * масштаб координат. У Незері 1 блок = 8 блоків Оверворлду, тож поріг 2000 без поправки означав би
 * 16 000 блоків Оверворлду — весь ігровий Незер сидів усередині гейта й кастомних істот там не
 * було взагалі.
 */
public final class SpawnDistance {

    private static final double NETHER_SCALE = 8.0;

    private SpawnDistance() {}

    public static boolean isFarEnough(Location loc, double minDistance) {
        if (loc == null || loc.getWorld() == null) return true;
        Location ws = loc.getWorld().getSpawnLocation();
        double min = loc.getWorld().getEnvironment() == World.Environment.NETHER
                ? minDistance / NETHER_SCALE
                : minDistance;
        return SpawnDistanceGate.isFarEnough(loc.getX() - ws.getX(), loc.getZ() - ws.getZ(), min);
    }
}
