package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Шукає блок вегетації БІЛЯ РІВНЯ ГРАВЦЯ (досяжний), а не на вершині стовпа: інакше у лісі
 * нода спавнилася б у кроні дерева — недосяжна для збору і захаращувала б простір.
 */
public final class ForageNodeLocation {

    private static final int ATTEMPTS = 10;
    private static final int SCAN_UP = 2;    // блоків над ногами гравця
    private static final int SCAN_DOWN = 3;  // блоків під ногами гравця

    private ForageNodeLocation() {}

    public static Optional<Location> findVegetationNear(Player player, Set<Material> vegetation, int radius) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null || vegetation.isEmpty()) return Optional.empty();

        int baseY = center.getBlockY();
        for (int i = 0; i < ATTEMPTS; i++) {
            int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            // Скануємо вузьке вертикальне вікно навколо ніг гравця — досяжна висота, не крона.
            for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
                Block b = world.getBlockAt(x, baseY + dy, z);
                if (vegetation.contains(b.getType())) {
                    return Optional.of(b.getLocation().add(0.5, 0.5, 0.5));
                }
            }
        }
        return Optional.empty();
    }
}
