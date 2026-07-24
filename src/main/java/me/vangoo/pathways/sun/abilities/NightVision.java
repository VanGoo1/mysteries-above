package me.vangoo.pathways.sun.abilities;

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
 * Sequence 8: Нічне бачення — активна здібність з періодичною ціною (той самий патерн, що
 * {@link Daytime}). Каст запускає {@link NightVisionSession}, що раз на вікно списує
 * духовність і оновлює ефект; вичерпалась — бачення згасає. Повторний каст вимикає його достроково.
 */
public class NightVision extends ActiveAbility {

    private static final int PERIODIC_COST = 3;
    private static final int COOLDOWN = 3;

    private final Map<UUID, NightVisionSession> sessions = new ConcurrentHashMap<>();
    private volatile IBeyonderContext beyonderContext;

    @Override
    public String getName() {
        return "Нічне бачення";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fДва мініатюрних сонця у ваших очах: дарують §bНічне бачення§f і не дають " +
                "§5Мороку§f вас засліпити. §7Підтримка: " + PERIODIC_COST + " духовності/с · " +
                "Гасне, коли скінчиться духовність.";
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

        // Тогл: повторний каст гасить активне бачення достроково.
        NightVisionSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Нічне бачення згашено", NamedTextColor.GRAY));
            return AbilityResult.success();
        }

        if (beyonderContext == null) {
            beyonderContext = context.beyonder();
        }

        NightVisionSession session = new NightVisionSession(casterId, PERIODIC_COST, beyonderContext, sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, NightVisionSession.REFRESH_PERIOD_TICKS, NightVisionSession.REFRESH_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow();

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Нічне бачення активне", NamedTextColor.AQUA));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        return AbilityResult.success();
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(NightVisionSession::cancel);
        sessions.clear();
    }
}
