package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр слідів, що лишає смерть на місці загибелі (Death, Посл. 7 і 6).
 *
 * <p>Два роди слідів, бо їх беруть різні здібності: <b>душа</b> (лише гравець, 30 хв) — її
 * бачить Сприйняття духів і допитує Голос мертвих; <b>труп</b> (будь-хто, 5 хв) — з нього
 * Воскресіння (Посл. 6) підіймає слугу. Тіло псується швидше за душу, тому строки різні.
 *
 * <p>Простий об'єкт, а не сервіс: його створює {@code Death.initializeAbilities()} й передає
 * конструктором здібностям одного шляху — {@link SpiritPerception} (записує смерті й малює
 * сліди), Голосу мертвих і Воскресінню. Тому ані {@code static}, ані запису в
 * {@code ServiceContainer} тут не треба.
 *
 * <p><b>Не персиститься навмисно.</b> Рестарт стирає всі сліди: 30 хвилин, що переживають
 * рестарт, — це вже не душа, що затрималась, а могила.
 *
 * <p>Ключ — UUID небіжчика, тож два медіуми онлайн (дві підписки на смерть) пишуть той самий
 * запис ідемпотентно, а нова смерть того ж гравця заміщає стару душу.
 */
public final class LingeringSouls {

    /** Спільне в обох слідів: де лежить і коли зникне ({@code expiresAt} — абсолютна мітка). */
    public sealed interface Trace permits Soul, Corpse {
        Location location();

        long expiresAt();
    }

    /** Душа на місці смерті гравця. */
    public record Soul(UUID victimId, String victimName, Location location, long expiresAt)
            implements Trace {
    }

    /** Тіло на місці смерті; {@code skeletal} — підійметься скелетом, а не зомбі. */
    public record Corpse(UUID victimId, String victimName, Location location, boolean skeletal,
                         long expiresAt) implements Trace {
    }

    private final Map<UUID, Soul> souls = new ConcurrentHashMap<>();
    private final Map<UUID, Corpse> corpses = new ConcurrentHashMap<>();

    /** Записує смерть; повторний запис по тому ж гравцеві заміщає попередню душу. */
    public void record(UUID victimId, String victimName, Location where) {
        if (victimId == null || where == null || where.getWorld() == null) return;
        souls.put(victimId, new Soul(victimId, victimName, where.clone(),
                System.currentTimeMillis() + SpiritMediumLore.SOUL_TTL_SECONDS * 1000L));
    }

    /** Записує тіло; повторна смерть того ж небіжчика заміщає попередній труп. */
    public void recordCorpse(UUID victimId, String victimName, Location where, boolean skeletal) {
        if (victimId == null || where == null || where.getWorld() == null) return;
        corpses.put(victimId, new Corpse(victimId, victimName, where.clone(), skeletal,
                System.currentTimeMillis() + SpiritGuideLore.CORPSE_TTL_SECONDS * 1000L));
    }

    /** Душу допитано — вона відходить (Голос мертвих споживає душу при взаємодії). */
    public void consume(UUID victimId) {
        souls.remove(victimId);
    }

    /** Тіло підняте — трупа на місці більше немає. */
    public void consumeCorpse(UUID victimId) {
        corpses.remove(victimId);
    }

    /**
     * Живі душі в радіусі від точки (той самий світ), крім душі {@code viewerId} — власну душу
     * гравець ні бачити, ні допитати не може: тоді хтось воскрес би сам себе. Протерміновані
     * чистяться тут же.
     */
    public List<Soul> near(Location origin, double radius, UUID viewerId) {
        return near(souls, origin, radius, viewerId);
    }

    /** Тіла в радіусі від точки (той самий світ), крім тіла {@code viewerId}. Див. {@link #near}. */
    public List<Corpse> corpsesNear(Location origin, double radius, UUID viewerId) {
        return near(corpses, origin, radius, viewerId);
    }

    private static <T extends Trace> List<T> near(Map<UUID, T> registry, Location origin,
                                                  double radius, UUID viewerId) {
        if (origin == null || origin.getWorld() == null) return List.of();

        long now = System.currentTimeMillis();
        double radiusSquared = radius * radius;
        List<T> found = new ArrayList<>();

        registry.values().removeIf(trace -> trace.expiresAt() <= now);
        for (Map.Entry<UUID, T> entry : registry.entrySet()) {
            if (entry.getKey().equals(viewerId)) continue;
            T trace = entry.getValue();
            if (!origin.getWorld().equals(trace.location().getWorld())) continue;
            if (trace.location().distanceSquared(origin) > radiusSquared) continue;
            found.add(trace);
        }
        return found;
    }
}
