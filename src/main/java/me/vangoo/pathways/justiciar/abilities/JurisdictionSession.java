package me.vangoo.pathways.justiciar.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Жива сесія "Сфери Юрисдикції" — один активний домен одного власника.
 * <p>
 * Це "сесія" з патерну рецепт→сесія: незмінні параметри (власник, центр, радіус) +
 * власний життєвий цикл (повторюваний тік, скасування). Сесія сама себе тікає через Bukkit —
 * жодного захопленого {@code IAbilityContext} (саме це прибирає баг {@code static globalContext}).
 */
public final class JurisdictionSession {

    private final UUID ownerId;
    private final Location center;
    private final int radius;
    private final long radiusSquared;
    private BukkitTask task;

    public JurisdictionSession(UUID ownerId, Location center, int radius) {
        this.ownerId = ownerId;
        this.center = center.clone();
        this.radius = radius;
        this.radiusSquared = (long) radius * radius;
    }

    /** Прив'язує повторюваний таск, щоб сесія могла скасувати саму себе. */
    public void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Зупиняє домен і його таск. Ідемпотентно. */
    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int radius() {
        return radius;
    }

    public boolean contains(Location loc) {
        if (loc == null || center.getWorld() == null) return false;
        if (!center.getWorld().equals(loc.getWorld())) return false;
        return loc.distanceSquared(center) <= radiusSquared;
    }

    /**
     * Один тік (раз на ігрову секунду): доки власник у межах власного домену —
     * підтримує на ньому Опір I та Квапливість I.
     */
    public void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        if (!contains(owner.getLocation())) return;

        owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0, false, false));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, 0, false, false));

        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            owner.getWorld().spawnParticle(Particle.WAX_OFF, owner.getLocation(), 1, 0.2, 0.1, 0.2);
        }
    }
}
