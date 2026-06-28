package me.vangoo.infrastructure.creatures;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Шукає безпечну ПОВЕРХНЕВУ точку на відстані [minR, maxR] від центру для ambient-спавну істоти:
 * тверда (не лава) земля + 2 блоки прохідного простору над нею. Кілька випадкових спроб; якщо
 * жодна не підходить — порожньо (планувальник просто пропускає тік).
 */
public final class AmbientSpawnLocation {

    private static final int ATTEMPTS = 8;

    private AmbientSpawnLocation() {}

    public static Optional<Location> findSurfaceNear(Location center, double minR, double maxR) {
        World world = center.getWorld();
        if (world == null) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
            double r = minR + ThreadLocalRandom.current().nextDouble() * (maxR - minR);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * r);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * r);

            Block ground = world.getHighestBlockAt(x, z);
            if (!ground.getType().isSolid() || ground.getType() == Material.LAVA) continue;

            Location loc = ground.getLocation().add(0.5, 1.0, 0.5);
            if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) {
                return Optional.of(loc);
            }
        }
        return Optional.empty();
    }
}
