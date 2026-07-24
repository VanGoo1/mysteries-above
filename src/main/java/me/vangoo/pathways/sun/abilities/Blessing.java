package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Благословення — активна аура з періодичною ціною (раніше тогл-пасивка).
 * Каст запускає {@link BlessingSession}, що раз на вікно списує духовність, знімає Виснаження
 * й Морок і оновлює Опір на власнику та союзниках поруч; вичерпалась духовність — аура гасне.
 * Поки активна — удари в ближньому бою завдають бонусної святої шкоди по темних/нежиті.
 * <p>
 * Бонусна шкода — через спільний {@code EntityDamageByEntityEvent}-хук (той самий патерн, що
 * {@code CleaveOfPurification}): хук глобальний для інстансу здібності (спільної для pathway),
 * тримає лише {@link IBeyonderContext} (без ідентичності кастера), перевіряє атакуючого проти
 * {@link #blessed} — instance-множини активних кастерів. Реєстр сесій — теж instance-поле.
 */
public class Blessing extends ActiveAbility {

    private static final int BASE_RANGE = 10;
    private static final int PERIODIC_COST = 15;
    private static final int COOLDOWN = 3;
    private static final int BASE_UNDEAD_BONUS = 3;

    private final Set<UUID> blessed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BlessingSession> sessions = new ConcurrentHashMap<>();
    private volatile IBeyonderContext beyonderContext;
    private volatile UUID trackingKey;

    @Override
    public String getName() {
        return "Благословення";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int amplifier = calculateAmplifier(sequence);
        int bonus = scaleValue(BASE_UNDEAD_BONUS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fОповиває себе й союзників поруч §bОпором %d§f, знімає Виснаження й Морок. " +
                        "Поки активне — ваші удари в ближньому бою завдають §c+%d §fсвятої шкоди по темних/нежиті.\n " +
                        "§7Радіус: %d бл · Гасне, коли скінчиться духовність.",
                amplifier + 1, bonus, range
        );
    }

    @Override
    public int getSpiritualityCost() {
        return PERIODIC_COST; // ціна першого вікна при активації
    }

    @Override
    public int getPeriodicCost() {
        return PERIODIC_COST; // показується в лорі предмета як «/сек»
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        ensureTracking(context);
        UUID casterId = context.getCasterId();

        // Тогл: повторний каст знімає активну ауру разом з ефектами.
        BlessingSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Благословення знято", NamedTextColor.GRAY));
            return AbilityResult.success();
        }

        Beyonder beyonder = context.getCasterBeyonder();
        Sequence sequence = beyonder.getSequence();
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int amplifier = calculateAmplifier(sequence);
        Color color = PathwayBranding.liquidOf(beyonder.getPathway().getName());

        blessed.add(casterId);
        BlessingSession session = new BlessingSession(
                casterId, range, amplifier, PERIODIC_COST, color,
                beyonderContext, context.effects(), blessed, sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, BlessingSession.REFRESH_PERIOD_TICKS, BlessingSession.REFRESH_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow(); // благословляє всіх у радіусі + спалах жовтої аури на кожному

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Благословення активне", NamedTextColor.GOLD));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        return AbilityResult.success();
    }

    private int calculateAmplifier(Sequence sequence) {
        int power = SequenceScaler.getSequencePower(sequence.level());
        if (power >= 8) return 2;
        if (power >= 5) return 1;
        return 0;
    }

    private void ensureTracking(IAbilityContext context) {
        if (trackingKey != null) return;
        synchronized (this) {
            if (trackingKey != null) return;
            beyonderContext = context.beyonder();
            UUID key = UUID.randomUUID();
            context.events().subscribeToTemporaryEvent(key,
                    EntityDamageByEntityEvent.class,
                    this::isBlessedMeleeHit,
                    this::applyUndeadBonus,
                    Integer.MAX_VALUE
            );
            trackingKey = key;
        }
    }

    private boolean isBlessedMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return false;
        if (!(event.getEntity() instanceof LivingEntity)) return false;
        return blessed.contains(attacker.getUniqueId());
    }

    private void applyUndeadBonus(EntityDamageByEntityEvent event) {
        Player attacker = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        if (!HolyTargetClassifier.isDarkOrUndead(target, beyonderContext)) return;

        Beyonder attackerBeyonder = beyonderContext.getBeyonder(attacker.getUniqueId());
        if (attackerBeyonder == null) return;

        double multiplier = HolyAffinity.damageMultiplier(true);
        int scaledBonus = scaleValue(BASE_UNDEAD_BONUS, attackerBeyonder.getSequence(), SequenceScaler.ScalingStrategy.MODERATE);
        int bonus = (int) Math.ceil(scaledBonus * multiplier);
        event.setDamage(event.getDamage() + bonus);
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(BlessingSession::cancel);
        sessions.clear();
        blessed.clear();
    }
}
