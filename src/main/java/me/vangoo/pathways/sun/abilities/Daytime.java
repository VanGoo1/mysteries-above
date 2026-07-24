package me.vangoo.pathways.sun.abilities;

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
 * Sequence 8: Денне світло — активна аура з періодичною ціною (той самий патерн, що
 * {@link Blessing}). Каст запускає {@link DaytimeSession}, що веде за кастером єдиний
 * невидимий блок {@link org.bukkit.Material#LIGHT} рівня 15 (ванільне поширення саме
 * сягає ~15 бл) і раз на вікно списує духовність; вичерпалась — світло гасне. Повторний
 * каст вимикає ауру достроково (тогл).
 */
public class Daytime extends ActiveAbility {

    private static final int PERIODIC_COST = 2;
    private static final int COOLDOWN = 3;

    private final Map<UUID, DaytimeSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Денне світло";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fОсвітлює довкола вас, наче настав день: світло природно поширюється далі §7(≈10+ бл)§f. " +
                "§7Підтримка: " + PERIODIC_COST + " духовності/с · Гасне, коли скінчиться духовність.";
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

        // Тогл: повторний каст гасить активне світло достроково.
        DaytimeSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Денне світло згашено", NamedTextColor.GRAY));
            return AbilityResult.success();
        }

        DaytimeSession session = new DaytimeSession(casterId, PERIODIC_COST, context.beyonder(), sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, DaytimeSession.RELOCATE_PERIOD_TICKS, DaytimeSession.RELOCATE_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow();

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Денне світло активне", NamedTextColor.GOLD));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        return AbilityResult.success();
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(DaytimeSession::cancel);
        sessions.clear();
    }
}
