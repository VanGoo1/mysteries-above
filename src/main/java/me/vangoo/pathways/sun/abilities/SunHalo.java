package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Німб Сонця — активна аура з періодичною ціною (той самий патерн, що
 * {@link Blessing} і {@link Daytime}). Каст запускає {@link SunHaloSession}, що щосекунди
 * лікує кастера й союзників поруч, карає темних/нежить поруч і оновлює золоте кільце над
 * головою; раз на вікно списує духовність — вичерпалась, аура гасне. Повторний каст вимикає
 * ауру достроково (тогл). На Seq 5 ("Німб очищення" з вікі) та сама здібність підсилюється:
 * більший радіус/шкода, зняття негативних ефектів із союзників і миттєве вбивство нежиті поруч
 * при активації — гейтиться за {@code sequence.level() <= 5}, окремого класу не потрібно.
 */
public class SunHalo extends ActiveAbility {

    private static final int BASE_RANGE = 8;
    private static final int ENHANCED_RANGE = 10;
    private static final int PERIODIC_COST = 20;
    private static final int ENHANCED_PERIODIC_COST = 30;
    private static final int COOLDOWN = 3;
    private static final int BASE_DAMAGE = 2;
    private static final int ENHANCED_DAMAGE = 4;

    private final Map<UUID, SunHaloSession> sessions = new ConcurrentHashMap<>();
    private volatile IBeyonderContext beyonderContext;

    private static boolean isEnhanced(Sequence sequence) {
        return sequence.level() <= 5;
    }

    @Override
    public String getName() {
        return "Німб Сонця";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int range = scaleValue(isEnhanced(sequence) ? ENHANCED_RANGE : BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int damage = scaleValue(isEnhanced(sequence) ? ENHANCED_DAMAGE : BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int cost = isEnhanced(sequence) ? ENHANCED_PERIODIC_COST : PERIODIC_COST;
        if (isEnhanced(sequence)) {
            return String.format(
                    "§fОповиває вас посиленим німбом святого світла (радіус §e%d §fбл): при активації миттєво " +
                            "вбиває нежить поруч.\nСоюзники поруч щосекунди отримують Регенерацію II й позбавляються " +
                            "негативних ефектів, темні й нежить поруч — §c%d §fшкоди та Слабкість.\n " +
                            "§7Підтримка: " + cost + " духовності/с · Гасне, коли скінчиться духовність.",
                    range, damage
            );
        }
        return String.format(
                "§fОповиває вас німбом святого світла (радіус §e%d §fбл): союзники поруч щосекунди " +
                        "отримують Регенерацію, темні й нежить поруч — §c%d §fшкоди та Слабкість.\n " +
                        "§7Підтримка: " + cost + " духовності/с · Гасне, коли скінчиться духовність.",
                range, damage
        );
    }

    @Override
    public int getSpiritualityCost() {
        return PERIODIC_COST; // ціна першого вікна при активації (нижня межа; реальна лічиться в performExecution)
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
        if (beyonderContext == null) {
            beyonderContext = context.beyonder();
        }
        UUID casterId = context.getCasterId();

        // Тогл: повторний каст гасить активний німб достроково.
        SunHaloSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✦ Німб згашено", NamedTextColor.GRAY));
            return AbilityResult.success();
        }

        Beyonder beyonder = context.getCasterBeyonder();
        Sequence sequence = beyonder.getSequence();
        boolean enhanced = isEnhanced(sequence);
        int range = scaleValue(enhanced ? ENHANCED_RANGE : BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int damage = scaleValue(enhanced ? ENHANCED_DAMAGE : BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int periodicCost = enhanced ? ENHANCED_PERIODIC_COST : PERIODIC_COST;
        Color color = PathwayBranding.liquidOf(beyonder.getPathway().getName());

        if (enhanced) {
            smiteUndeadOnActivation(context, range);
        }

        SunHaloSession session = new SunHaloSession(
                casterId, range, damage, periodicCost, color, enhanced,
                beyonderContext, context.effects(), context.amplification(), sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, SunHaloSession.REFRESH_PERIOD_TICKS, SunHaloSession.REFRESH_PERIOD_TICKS);
        session.bindTask(task);
        session.start();
        sessions.put(casterId, session);

        context.messaging().sendMessageToActionBar(casterId,
                Component.text(enhanced ? "✦ Німб очищення активний" : "✦ Німб Сонця активний", NamedTextColor.GOLD));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, enhanced ? 1.6f : 1.8f);
        return AbilityResult.success();
    }

    /**
     * Вікі Seq 5: "instantly kills undead on activation". Обмежено ванільною нежиттю
     * (не гравцями "темних" pathway, яких {@link HolyTargetClassifier} теж класифікує як
     * "dark") — миттєве вбивство гравця при активації було б непропорційним PvP-вбивством
     * одним кліком, а вікі описує це саме для нежиті, не для ворожих Beyonder'ів.
     */
    private void smiteUndeadOnActivation(IAbilityContext context, int range) {
        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (entity instanceof Player) continue;
            if (!HolyTargetClassifier.isDarkOrUndead(entity, context)) continue;

            context.entity().damage(entity.getUniqueId(), entity.getHealth() + 1);
            context.effects().spawnParticle(Particle.END_ROD, entity.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3);
        }

        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.4f);
        context.effects().playExplosionRingEffect(context.getCasterLocation().add(0, 1, 0), range * 0.4, Particle.DUST, gold);
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(SunHaloSession::cancel);
        sessions.clear();
    }
}
