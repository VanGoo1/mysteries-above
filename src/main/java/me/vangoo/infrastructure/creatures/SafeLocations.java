package me.vangoo.infrastructure.creatures;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Optional;

/** Знаходить прохідне (2 блоки заввишки) місце поряд із origin для безпечного спавну/телепорту. */
public final class SafeLocations {

    private SafeLocations() {}

    /**
     * Телепорт: місце знайдеться завжди — у крайньому разі точка над origin. Годиться для
     * переміщення вже наявної сутності (гірше місце краще за скасований телепорт), але НЕ для
     * спавну: там фолбек означає моба в стіні — див. {@link #spawnableNear}.
     */
    public static Location passableNear(Location origin) {
        int[][] offsets = { {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {2, 1, 0}, {0, 1, 2}, {0, 0, 0} };
        for (int[] o : offsets) {
            Location cand = origin.clone().add(o[0] + 0.5, o[1], o[2] + 0.5);
            if (isPassableColumn(cand)) return cand;
        }
        return origin.clone().add(0.5, 1.0, 0.5);
    }

    /**
     * Спавн: найближче до origin місце з твердою підлогою і 2 блоками простору, не ближче
     * {@code minRadius} блоків по горизонталі. Порожньо, якщо такого місця немає — тоді істота
     * НЕ з'являється взагалі (краще ніж у стіні впритул до гравця).
     */
    public static Optional<Location> spawnableNear(Location origin, int minRadius, int maxRadius) {
        if (origin.getWorld() == null) return Optional.empty();
        for (int d = Math.max(1, minRadius); d <= maxRadius; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue; // лише кільце радіуса d
                    for (int dy : new int[]{0, 1, -1, 2, -2, 3}) {
                        Location cand = origin.clone().add(dx + 0.5, dy, dz + 0.5);
                        if (isSpawnable(cand)) return Optional.of(cand);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Два блоки прохідного простору (для телепорту достатньо). */
    private static boolean isPassableColumn(Location loc) {
        return loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable();
    }

    /** Для спавну ще й тверда підлога під ногами і жодної рідини — інакше моб тоне або падає. */
    private static boolean isSpawnable(Location loc) {
        if (!isPassableColumn(loc)) return false;
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        if (feet.isLiquid() || head.isLiquid()) return false;
        return loc.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }
}
