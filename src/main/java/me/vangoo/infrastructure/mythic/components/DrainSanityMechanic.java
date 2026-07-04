package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.mythic.MythicBridge;

@MythicMechanic(author = "mysteries-above", name = "drainsanity",
        description = "Increases sanity loss of the target Beyonder")
public class DrainSanityMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final int amount;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public DrainSanityMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.amount = event.getConfig().getInteger(new String[]{"amount", "a"}, 1);
    }

    // Скіл-клок MythicMobs асинхронний; мутація Beyonder має йти на main thread,
    // щоб не гнатися з рештою доменної логіки
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!target.isPlayer()) return SkillResult.INVALID_TARGET;
        var service = MythicBridge.beyonders();
        if (service == null) return SkillResult.CONDITION_FAILED;
        Beyonder b = service.getBeyonder(target.getUniqueId());
        if (b == null) return SkillResult.CONDITION_FAILED;
        b.increaseSanityLoss(amount);
        service.updateBeyonder(b);
        return SkillResult.SUCCESS;
    }
}
