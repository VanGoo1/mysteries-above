package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.mythic.MythicBridge;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Моб Помилки краде в гравця одну силу — та сама механіка, що й у гравця-Прометея:
 * запис у {@code TheftLedger} під UUID моба, а примус — єдиний {@code if} у
 * {@code AbilityExecutor}. Мобу сила ні на що не потрібна; сенс у тому, що жертва
 * її не кличе. Запис самознищується підмітанням ledger'а, тож смерть моба нічого
 * не ламає, а вікно переживає релог і рестарт.
 */
@MythicMechanic(author = "mysteries-above", name = "stealability",
        description = "Steals one ability from the target Beyonder for N seconds")
public class AbilityTheftMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final int seconds;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public AbilityTheftMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.seconds = event.getConfig().getInteger(new String[]{"seconds", "s"}, 90);
    }

    // Скіл-клок MythicMobs асинхронний; запис у ledger — на main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(target.getBukkitEntity() instanceof Player victim)) return SkillResult.INVALID_TARGET;
        var ledger = MythicBridge.thefts();
        var service = MythicBridge.beyonders();
        if (ledger == null || service == null) return SkillResult.CONDITION_FAILED;
        Beyonder beyonder = service.getBeyonder(victim.getUniqueId());
        if (beyonder == null) return SkillResult.CONDITION_FAILED;

        // Красти є сенс лише активні: пасивку жертва й так не «кличе»
        List<Ability> stealable = beyonder.getAbilities().stream()
                .filter(a -> a.getType() == AbilityType.ACTIVE)
                .filter(a -> !ledger.isSuppressed(victim.getUniqueId(), a.getIdentity()))
                .toList();
        if (stealable.isEmpty()) return SkillResult.CONDITION_FAILED;

        Ability stolen = stealable.get(ThreadLocalRandom.current().nextInt(stealable.size()));
        long millis = seconds * 1000L;
        // reserveHold == suppression: мобу тримати силу ні до чого, тож обидва вікна
        // гаснуть разом і підмітання викидає запис одним проходом
        ledger.add(data.getCaster().getEntity().getUniqueId(), victim.getUniqueId(),
                stolen.getIdentity(), millis, 0L, millis);

        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
        victim.sendMessage(ChatColor.DARK_PURPLE + "У вас вкрали силу «"
                + ChatColor.LIGHT_PURPLE + stolen.getName() + ChatColor.DARK_PURPLE
                + "» — вона не відгукнеться " + (seconds / 60) + " хв " + (seconds % 60) + " с.");
        return SkillResult.SUCCESS;
    }
}
