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
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

@MythicMechanic(author = "mysteries-above", name = "blinkbehind",
        description = "Teleports the caster behind the target when the spot is passable")
public class BlinkBehindMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final double distance;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public BlinkBehindMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.distance = event.getConfig().getDouble(new String[]{"distance", "d"}, 2.0);
    }

    // teleport — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity caster = data.getCaster().getEntity().getBukkitEntity();
        Entity victim = target.getBukkitEntity();
        Vector facing = victim.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(1, 0, 0); // ціль дивиться вертикально — довільний напрямок
        }
        Location behind = victim.getLocation().clone()
                .subtract(facing.normalize().multiply(distance));
        Location dest = SafeLocations.passableNear(behind);
        if (!dest.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            return SkillResult.CONDITION_FAILED; // немає опори — блінк скасовано, ГКД уже витрачено
        }
        dest.setDirection(victim.getLocation().toVector().subtract(dest.toVector())); // обличчям до цілі
        caster.teleport(dest);
        return SkillResult.SUCCESS;
    }
}
