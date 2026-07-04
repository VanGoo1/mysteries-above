package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import me.vangoo.infrastructure.mythic.MythicBridge;

@MythicCondition(author = "mysteries-above", name = "isbeyonder",
        description = "True if the target player is a Beyonder")
public class IsBeyonderCondition extends SkillCondition implements IEntityCondition {

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public IsBeyonderCondition(MythicConditionLoadEvent event) {
        super(event.getConfig().getLine());
    }

    @Override
    public boolean check(AbstractEntity target) {
        if (!target.isPlayer()) return false;
        var service = MythicBridge.beyonders();
        return service != null && service.getBeyonder(target.getUniqueId()) != null;
    }
}
