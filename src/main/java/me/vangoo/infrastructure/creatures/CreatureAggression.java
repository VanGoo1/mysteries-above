package me.vangoo.infrastructure.creatures;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Робить істоту агресивною: одразу націлюється на найближчого гравця, якщо ціль відсутня/мертва. */
public final class CreatureAggression {

    private CreatureAggression() {}

    public static void acquireTarget(LivingEntity entity, double range) {
        if (!(entity instanceof Mob mob)) return;
        LivingEntity current = mob.getTarget();
        if (current != null && !current.isDead()) return;

        Player nearest = null;
        double best = range * range;
        for (Entity e : entity.getNearbyEntities(range, range, range)) {
            if (e instanceof Player p && !p.isDead() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                double d = p.getLocation().distanceSquared(entity.getLocation());
                if (d <= best) {
                    best = d;
                    nearest = p;
                }
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
        }
    }
}
