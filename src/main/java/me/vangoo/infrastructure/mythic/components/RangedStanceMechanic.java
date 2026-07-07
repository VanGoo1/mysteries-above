package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Автомат станів RANGED-стійки (рухові AI-цілі шаблону вимкнені — рух веде механіка):
 * ціль далі max → APPROACH (іде в смугу); у смузі min..max → HOLD (стоїть, дивиться, кастує);
 * ближче min → BACKOFF (відходить кроком до валідної точки); спровокований (PDC від provoke)
 * → PROVOKED (іде на ціль і б'є ванільним мілі з атрибута Damage).
 */
@MythicMechanic(author = "mysteries-above", name = "rangedstance",
        description = "Ranged stance state machine: approach, hold and cast, walk away, melee when provoked")
public class RangedStanceMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private static final long ATTACK_INTERVAL_MILLIS = 1000L;
    private static final double MELEE_REACH = 2.2;
    private static final double BACKOFF_DISTANCE = 7.0;

    private final double min;
    private final double max;
    // per-entity час останнього мілі-удару в PROVOKED; мертві записи чистяться в purgeDeadEntries
    private final Map<UUID, Long> lastAttackAt = new ConcurrentHashMap<>();

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public RangedStanceMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.min = event.getConfig().getDouble(new String[]{"min"}, 5.0);
        this.max = event.getConfig().getDouble(new String[]{"max"}, 11.0);
    }

    // Навігація/атака/PDC — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(data.getCaster().getEntity().getBukkitEntity() instanceof Mob mob)
                || !(target.getBukkitEntity() instanceof LivingEntity victim)) {
            return SkillResult.CONDITION_FAILED;
        }

        long now = System.currentTimeMillis();
        if (isProvoked(mob, now)) {
            meleeRetaliate(mob, victim, now);
            return SkillResult.SUCCESS;
        }

        double distance = mob.getLocation().distance(victim.getLocation());
        if (distance > max) {
            mob.getPathfinder().moveTo(victim, 1.05);
        } else if (distance < min) {
            backOff(mob, victim);
        } else {
            hold(mob, victim);
        }
        return SkillResult.SUCCESS;
    }

    private boolean isProvoked(Mob mob, long now) {
        Long until = mob.getPersistentDataContainer().get(StanceKeys.PROVOKED_UNTIL, PersistentDataType.LONG);
        return until != null && now < until;
    }

    // PROVOKED: іде на кривдника; впритул — ванільний удар (swing + knockback + атрибут Damage)
    private void meleeRetaliate(Mob mob, LivingEntity victim, long now) {
        mob.getPathfinder().moveTo(victim, 1.15);
        if (mob.getLocation().distance(victim.getLocation()) > MELEE_REACH) return;
        long last = lastAttackAt.getOrDefault(mob.getUniqueId(), 0L);
        if (now - last < ATTACK_INTERVAL_MILLIS) return;
        if (mob.getAttribute(Attribute.ATTACK_DAMAGE) == null) return; // Type без атрибута атаки (Shulker) — без мілі
        mob.attack(victim);
        lastAttackAt.put(mob.getUniqueId(), now);
        purgeDeadEntries();
    }

    private void hold(Mob mob, LivingEntity victim) {
        mob.getPathfinder().stopPathfinding();
        mob.lookAt(victim);
    }

    // BACKOFF: крок до точки ~7 блоків у протилежний бік; нема валідної точки — тримає позицію
    private void backOff(Mob mob, LivingEntity victim) {
        Vector away = mob.getLocation().toVector().subtract(victim.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) away = new Vector(1, 0, 0);
        Location dest = SafeLocations.passableNear(
                mob.getLocation().clone().add(away.normalize().multiply(BACKOFF_DISTANCE)));
        var below = dest.clone().subtract(0, 1, 0).getBlock();
        boolean lava = dest.getBlock().getType() == Material.LAVA || below.getType() == Material.LAVA;
        if (!below.getType().isSolid() || lava) {
            hold(mob, victim);
            return;
        }
        mob.getPathfinder().moveTo(dest, 1.15);
    }

    private void purgeDeadEntries() {
        if (ThreadLocalRandom.current().nextInt(50) != 0) return;
        lastAttackAt.keySet().removeIf(id -> Bukkit.getEntity(id) == null);
    }
}
