package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Шукає блок вегетації біля гравця для розміщення ноди фореджу. */
public final class ForageNodeLocation {

    private static final int ATTEMPTS = 10;

    private ForageNodeLocation() {}

    public static Optional<Location> findVegetationNear(Player player, Set<Material> vegetation, int radius) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null || vegetation.isEmpty()) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            int dx = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int dz = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int x = center.getBlockX() + dx;
            int z = center.getBlockZ() + dz;

            Block top = world.getHighestBlockAt(x, z);
            for (int dy = 0; dy >= -2; dy--) {
                Block b = top.getRelative(0, dy, 0);
                if (vegetation.contains(b.getType())) {
                    return Optional.of(b.getLocation().add(0.5, 0.6, 0.5));
                }
            }
        }
        return Optional.empty();
    }
}
