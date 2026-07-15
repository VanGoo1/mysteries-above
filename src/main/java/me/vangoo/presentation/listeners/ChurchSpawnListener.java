package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.organizations.ChurchSiteService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Random;

/**
 * Знайдене село → поруч спавниться випадкова ще не розміщена церква (кожна — раз на світ).
 * Ключ села — min-кут bbox структурного старту; оброблені села персистяться.
 */
public class ChurchSpawnListener implements Listener {

    private final ChurchSiteService sites;
    private final int villageOffset;
    private final Random random = new Random();

    public ChurchSpawnListener(ChurchSiteService sites, int villageOffset) {
        this.sites = sites;
        this.villageOffset = villageOffset;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (GeneratedStructure structure : event.getChunk().getStructures()) {
            NamespacedKey key = Registry.STRUCTURE.getKey(structure.getStructure());
            if (key == null || !key.getKey().startsWith("village")) {
                continue;
            }
            BoundingBox box = structure.getBoundingBox();
            String villageKey = event.getWorld().getName()
                    + ":" + (int) box.getMinX() + ":" + (int) box.getMinZ();
            if (sites.isVillageProcessed(villageKey)) {
                continue;
            }
            List<String> unplaced = sites.unplacedChurchIds();
            if (unplaced.isEmpty()) {
                sites.markVillageProcessed(villageKey); // усі 10 розміщені — більше не перевіряти
                continue;
            }
            String churchId = unplaced.get(random.nextInt(unplaced.size()));
            Location spot = pickSpot(event.getWorld(), box);
            sites.autoPlace(villageKey, churchId, spot);
        }
    }

    /** Точка за межею села: випадкова сторона світу + офсет, y — поверхня. */
    private Location pickSpot(World world, BoundingBox box) {
        int side = random.nextInt(4);
        double x = switch (side) {
            case 0 -> box.getMaxX() + villageOffset;
            case 1 -> box.getMinX() - villageOffset;
            default -> box.getCenterX();
        };
        double z = switch (side) {
            case 2 -> box.getMaxZ() + villageOffset;
            case 3 -> box.getMinZ() - villageOffset;
            default -> box.getCenterZ();
        };
        int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
        return new Location(world, x, y, z);
    }
}
