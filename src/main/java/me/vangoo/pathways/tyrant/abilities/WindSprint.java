package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 6: Вітряний біг — тогл із періодичною ціною (той самий патерн, що
 * {@link WindFlight}). Каст запускає {@link WindSprintSession}: вітер несе бігуна удвічі
 * швидше й лишає слід біля ніг. Повторний каст вимикає; скінчилась духовність — вітер стихає.
 */
public class WindSprint extends ActiveAbility {

    private static final int PERIODIC_COST = 15;
    private static final int COOLDOWN = 3;

    private final Map<UUID, WindSprintSession> sessions = new ConcurrentHashMap<>();
    private volatile IBeyonderContext beyonderContext;

    @Override
    public String getName() {
        return "Вітряний біг";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fВітер підхоплює ваш крок: ви біжите §bдуже швидко§f, лишаючи за собою " +
                "вітряний слід..";
    }

    @Override
    public int getSpiritualityCost() {
        return PERIODIC_COST; // ціна першого вікна при активації
    }

    @Override
    public int getPeriodicCost() {
        return PERIODIC_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Тогл: повторний каст зупиняє біг достроково.
        WindSprintSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Вітер стих", NamedTextColor.GRAY));
            return AbilityResult.success();
        }

        if (beyonderContext == null) {
            beyonderContext = context.beyonder();
        }

        WindSprintSession session = new WindSprintSession(casterId, PERIODIC_COST, beyonderContext, sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, WindSprintSession.REFRESH_PERIOD_TICKS, WindSprintSession.REFRESH_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow();

        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.5f);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Вітряний біг активний", NamedTextColor.AQUA));
        return AbilityResult.success();
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(WindSprintSession::cancel);
        sessions.clear();
    }
}
