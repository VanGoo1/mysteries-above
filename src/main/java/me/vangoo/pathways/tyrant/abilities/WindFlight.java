package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 6: Політ на вітрі — об'єднані Політ + Ковзання + Ширяння (той самий патерн
 * з періодичною ціною, що {@link me.vangoo.pathways.sun.abilities.NightVision}). Каст
 * запускає {@link WindFlightSession}: у режимі Польоту вітер тримає в повітрі (ширяння —
 * властивість вільного польоту), у режимі Ковзання — лагідно опускає. Shift+каст перемикає
 * режим; коли політ ззовні заборонено, лишається тільки ковзання. Вичерпалась духовність —
 * вітер стихає й опускає на землю. Повторний каст без Shift вимикає.
 */
public class WindFlight extends ActiveAbility {

    /** База, з якої множник Sequence виводить ціну підтримки: Seq 6 — 35/с, Seq 5 — 32/с, Seq 0 — 22/с. */
    private static final int BASE_PERIODIC_COST = 50;
    /** Ціна на Seq 6, де політ відкривається — показується, коли Sequence невідома. */
    private static final int UNLOCK_COST = 35;
    private static final int COOLDOWN = 3;

    private final Map<UUID, WindFlightSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, WindFlightSession.Mode> preferredMode = new ConcurrentHashMap<>();
    private volatile IBeyonderContext beyonderContext;

    @Override
    public String getName() {
        return "Політ на вітрі";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fВітер підіймає вас у небо: §bПольот§f тримає в повітрі, а " +
                "§bКовзання§f лагідно опускає.\n§7Shift+каст — перемкнути режим";
    }

    @Override
    public int getSpiritualityCost() {
        return UNLOCK_COST; // ціна першого вікна при активації
    }

    @Override
    public int getPeriodicCost() {
        return UNLOCK_COST;
    }

    /** Чим сильніший Beyonder, тим дешевше вітер тримає його в повітрі — довший політ на ту саму духовність. */
    @Override
    public int getPeriodicCost(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.MODERATE);
        return (int) Math.ceil(BASE_PERIODIC_COST / multiplier);
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        boolean sneaking = context.playerData().isSneaking(casterId);
        WindFlightSession active = sessions.get(casterId);

        if (active != null) {
            // Shift — перемкнути режим (без ціни й кулдауну, як mode-switch у Заборони).
            if (sneaking) {
                WindFlightSession.Mode mode = active.switchMode();
                preferredMode.put(casterId, mode);
                context.messaging().sendMessageToActionBar(casterId,
                        Component.text("✦ Режим: " + mode.displayName(), NamedTextColor.AQUA));
                context.effects().playSoundForPlayer(casterId, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.4f);
                return AbilityResult.deferred();
            }
            // Повторний каст — вимкнути.
            active.cancel();
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Вітер стих", NamedTextColor.GRAY));
            return AbilityResult.success();
        }

        WindFlightSession.Mode preferred =
                preferredMode.getOrDefault(casterId, WindFlightSession.Mode.FLIGHT);

        // Shift, коли політ вимкнено — лише перемикає режим для наступного зльоту, НЕ активує.
        if (sneaking) {
            preferred = preferred == WindFlightSession.Mode.FLIGHT
                    ? WindFlightSession.Mode.GLIDE : WindFlightSession.Mode.FLIGHT;
            preferredMode.put(casterId, preferred);
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Режим наступного зльоту: " + preferred.displayName(), NamedTextColor.GRAY));
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.4f);
            return AbilityResult.deferred();
        }

        if (beyonderContext == null) {
            beyonderContext = context.beyonder();
        }

        WindFlightSession session = new WindFlightSession(casterId,
                getPeriodicCost(context.getCasterBeyonder().getSequence()),
                beyonderContext, sessions, preferred);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, WindFlightSession.REFRESH_PERIOD_TICKS, WindFlightSession.REFRESH_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow(); // початковий порив вітру + вітряний шлейф веде сесія

        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.2f);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Політ на вітрі активний", NamedTextColor.AQUA));
        return AbilityResult.success();
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(WindFlightSession::cancel);
        sessions.clear();
    }
}
