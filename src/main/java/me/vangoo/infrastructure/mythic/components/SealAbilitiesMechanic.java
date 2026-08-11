package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.infrastructure.mythic.MythicBridge;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Печатка розуму: на кілька секунд гравець не може викликати ЖОДНОЇ здібності.
 * Примус — уже наявний {@code AbilityLockManager} (єдина перевірка в
 * {@code AbilityExecutor.execute}), тож власного стану механіка не тримає.
 */
@MythicMechanic(author = "mysteries-above", name = "sealabilities",
        description = "Locks all abilities of the target Beyonder for a few seconds")
public class SealAbilitiesMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final int seconds;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public SealAbilitiesMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.seconds = event.getConfig().getInteger(new String[]{"seconds", "s"}, 5);
    }

    // Скіл-клок MythicMobs асинхронний; лок і повідомлення — на main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(target.getBukkitEntity() instanceof Player victim)) return SkillResult.INVALID_TARGET;
        var locks = MythicBridge.abilityLocks();
        var service = MythicBridge.beyonders();
        if (locks == null || service == null) return SkillResult.CONDITION_FAILED;
        // Не-Beyonder'у пломбувати нічого — лок був би невидимим
        if (service.getBeyonder(victim.getUniqueId()) == null) return SkillResult.CONDITION_FAILED;

        locks.lockPlayer(victim.getUniqueId(), seconds);
        victim.playSound(victim.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 0.5f);
        victim.sendMessage(ChatColor.DARK_PURPLE + "Ваш розум запечатано — здібності мовчать "
                + seconds + " с.");
        return SkillResult.SUCCESS;
    }
}
