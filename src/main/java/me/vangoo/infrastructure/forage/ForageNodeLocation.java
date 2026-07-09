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
 * Шукає блок вегетації: наземну флору БІЛЯ РІВНЯ ГРАВЦЯ (досяжну) та листя у кронах дерев
 * (скан згори вниз від найвищого блока колонки). Наземний пошук навмисно не сканує вершину
 * стовпа: інакше у лісі нода спавнилася б у кроні дерева — недосяжна для збору і захаращувала б простір.
 */
public final class ForageNodeLocation {

    private static final int ATTEMPTS = 10;
    private static final int SCAN_UP = 2;    // блоків над ногами гравця
    private static final int SCAN_DOWN = 3;  // блоків під ногами гравця
    private static final int CANOPY_SCAN_DEPTH = 4; // блоків углиб крони від найвищого блока

    private ForageNodeLocation() {}

    public static Optional<Block> findVegetationNear(Player player, Set<Material> vegetation, int radius) {
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
                    return Optional.of(b);
                }
            }
        }
        return Optional.empty();
    }

    /** Шукає блок листя у кронах: згори (найвищий блок колонки) вниз кілька блоків. */
    public static Optional<Block> findLeavesNear(Player player, Set<Material> leaves, int radius) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null || leaves.isEmpty()) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int topY = world.getHighestBlockYAt(x, z);
            for (int dy = 0; dy < CANOPY_SCAN_DEPTH; dy++) {
                Block b = world.getBlockAt(x, topY - dy, z);
                if (leaves.contains(b.getType())) return Optional.of(b);
            }
        }
        return Optional.empty();
    }
}
