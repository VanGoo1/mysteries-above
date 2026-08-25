package me.vangoo.infrastructure.creatures;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Шукає безпечну точку на відстані [minR, maxR] від центру для ambient-спавну істоти.
 * Два режими:
 * <ul>
 *   <li><b>Наземний</b> — тверда (не лава) земля + 2 блоки прохідного простору над нею.</li>
 *   <li><b>Водний</b> — водний стовп (мінімум 2 блоки WATER вертикально).</li>
 * </ul>
 * Кілька випадкових спроб; якщо жодна не підходить — порожньо (планувальник просто пропускає тік).
 */
public final class AmbientSpawnLocation {

    private static final int ATTEMPTS = 8;

    private static final Set<String> AQUATIC = Set.of(
            "GUARDIAN", "ELDER_GUARDIAN", "DROWNED", "SQUID", "GLOW_SQUID", "DOLPHIN",
            "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH", "AXOLOTL", "TURTLE", "TADPOLE");

    /** Чи живе базовий ентіті у воді (для них ambient-точка шукається у водному стовпі). */
    public static boolean isAquatic(String baseEntityType) {
        return baseEntityType != null && AQUATIC.contains(baseEntityType.toUpperCase(Locale.ROOT));
    }

    private AmbientSpawnLocation() {}

    /**
     * Шукає точку спавну поблизу центру.
     *
     * @param aquatic {@code true} — шукати водний стовп (для водних істот);
     *                {@code false} — шукати тверду землю + 2 прохідні блоки.
     */
    public static Optional<Location> findSpawnNear(Location center, double minR, double maxR, boolean aquatic) {
        World world = center.getWorld();
        if (world == null) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
            double r = minR + ThreadLocalRandom.current().nextDouble() * (maxR - minR);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * r);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * r);

            Block top = surfaceAt(world, x, z, center.getBlockY());

            if (aquatic) {
                // потрібен водний стовп (не калюжа в один блок)
                if (top.getType() == Material.WATER && top.getRelative(0, -1, 0).getType() == Material.WATER) {
                    return Optional.of(top.getLocation().add(0.5, 0.0, 0.5));
                }
                continue;
            }

            if (!top.getType().isSolid() || top.getType() == Material.LAVA) continue;
            Location loc = top.getLocation().add(0.5, 1.0, 0.5);
            if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) {
                return Optional.of(loc);
            }
        }
        return Optional.empty();
    }

    /**
     * Опора під точкою (x, z). У світі зі стелею (Незер) {@code getHighestBlockAt} віддає бедрок
     * даху, тож ambient-істоти ставились на дах Незеру замість того, щоб прийти до гравця —
     * там шукаємо перший твердий блок ВНИЗ від рівня центру.
     */
    private static Block surfaceAt(World world, int x, int z, int centerY) {
        if (!world.hasCeiling()) return world.getHighestBlockAt(x, z);
        int from = Math.min(centerY + 8, world.getMaxHeight() - 1);
        for (int y = from; y > world.getMinHeight(); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().isSolid()) return b;
        }
        return world.getBlockAt(x, world.getMinHeight(), z);
    }
}
