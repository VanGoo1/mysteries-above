package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.persistence.PersistentDataType;

@MythicMechanic(author = "mysteries-above", name = "provoke",
        description = "Marks the target mob as provoked (melee retaliation window) via a PDC timestamp")
public class ProvokeMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final long windowMillis;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public ProvokeMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.windowMillis = event.getConfig().getInteger(new String[]{"seconds", "s"}, 8) * 1000L;
    }

    // PDC — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        target.getBukkitEntity().getPersistentDataContainer().set(StanceKeys.PROVOKED_UNTIL,
                PersistentDataType.LONG, System.currentTimeMillis() + windowMillis);
        return SkillResult.SUCCESS;
    }
}
