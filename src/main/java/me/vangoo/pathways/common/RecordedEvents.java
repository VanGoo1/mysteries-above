package me.vangoo.pathways.common;

import me.vangoo.domain.valueobjects.RecordedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Конвертація слідів CoreProtect у координати світу. Живе в {@code pathways.common},
 * бо сліди читають кілька шляхів (Error, Death, Door, WhiteTower, ритуали), а сам
 * {@link RecordedEvent} тримає лише примітиви — той самий поділ, що у
 * {@code UndeadRetinue.toLocation(RetinuePoint)}.
 */
public final class RecordedEvents {

    private RecordedEvents() {
    }

    /**
     * @return місце події або {@code null}, якщо світ уже вивантажений — викликач мусить це
     *         врахувати, бо сліди переживають вивантаження світу.
     */
    public static Location toLocation(RecordedEvent event) {
        World world = Bukkit.getWorld(event.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, event.getX(), event.getY(), event.getZ());
    }

    /**
     * Ключ блока для дедуплікації слідів: одна перекладена скриня інакше читається
     * як десятки окремих подій. Рахується з примітивів, {@code Location} не потрібен.
     */
    public static String blockKey(RecordedEvent event) {
        return (int) Math.floor(event.getX()) + ":"
                + (int) Math.floor(event.getY()) + ":"
                + (int) Math.floor(event.getZ());
    }
}
