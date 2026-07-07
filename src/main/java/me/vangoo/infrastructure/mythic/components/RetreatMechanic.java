package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

@MythicMechanic(author = "mysteries-above", name = "retreat",
        description = "Pushes the caster away from the target (kite hop for ranged stances)")
public class RetreatMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final double strength;
    private final double vertical;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public RetreatMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.strength = event.getConfig().getDouble(new String[]{"strength", "str"}, 1.0);
        this.vertical = event.getConfig().getDouble(new String[]{"vertical", "vy"}, 0.35);
    }

    // setVelocity — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity caster = data.getCaster().getEntity().getBukkitEntity();
        Vector away = caster.getLocation().toVector()
                .subtract(target.getBukkitEntity().getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(1, 0, 0); // ціль у тій самій точці — довільний горизонтальний напрямок
        }
        caster.setVelocity(away.normalize().multiply(strength).setY(vertical));
        return SkillResult.SUCCESS;
    }
}
