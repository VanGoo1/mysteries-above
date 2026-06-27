package me.vangoo.infrastructure.creatures;

import org.bukkit.Location;

/** Знаходить прохідне (2 блоки заввишки) місце поряд із origin для безпечного спавну/телепорту. */
public final class SafeLocations {

    private SafeLocations() {}

    public static Location passableNear(Location origin) {
        int[][] offsets = { {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {2, 1, 0}, {0, 1, 2}, {0, 0, 0} };
        for (int[] o : offsets) {
            Location cand = origin.clone().add(o[0] + 0.5, o[1], o[2] + 0.5);
            if (cand.getBlock().isPassable() && cand.clone().add(0, 1, 0).getBlock().isPassable()) {
                return cand;
            }
        }
        return origin.clone().add(0.5, 1.0, 0.5);
    }
}
