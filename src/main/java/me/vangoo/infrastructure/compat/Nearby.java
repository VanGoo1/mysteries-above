package me.vangoo.infrastructure.compat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Пошук сутностей у радіусі — заміна Paper'ових {@code World.getNearbyPlayers} /
 * {@code getNearbyLivingEntities}, яких на Spigot-API немає.
 *
 * <p>Ванільний {@code World.getNearbyEntities} бере КУБ, а Paper'ові методи — СФЕРУ, тож
 * результат додатково фільтрується за квадратом відстані: інакше ефект чіпляв би цілі по
 * кутах куба на ~1.7 радіуса.
 */
public final class Nearby {

    private Nearby() {
    }

    public static List<Player> players(Location center, double radius) {
        return collect(center, radius, Player.class);
    }

    public static List<LivingEntity> living(Location center, double radius) {
        return collect(center, radius, LivingEntity.class);
    }

    private static <T extends Entity> List<T> collect(Location center, double radius, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (center == null || center.getWorld() == null) return found;

        double radiusSquared = radius * radius;
        Collection<Entity> box = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : box) {
            if (!type.isInstance(entity)) continue;
            if (entity.getLocation().distanceSquared(center) > radiusSquared) continue;
            found.add(type.cast(entity));
        }
        return found;
    }
}
