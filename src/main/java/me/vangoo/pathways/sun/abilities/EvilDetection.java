package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EvilDetection extends PermanentPassiveAbility {

    private static final int BASE_RANGE = 20;
    private static final int SCAN_PERIOD_TICKS = 20; // раз на секунду

    private int tickCounter = 0;

    /**
     * Хто зараз світиться (по кастеру). Без цього рефреш глоу-виклику "перебивав" би сам
     * себе: {@code GlowingContext.setGlowing(..., durationTicks)} планує незалежний
     * авто-removeGlowing, який спрацьовує навіть після повторного {@code setGlowing} —
     * саме звідси й було блимання. Тримаємо власний реєстр і знімаємо світіння лише тоді,
     * коли ціль реально вийшла з радіусу/зникла, а не покладаємось на таймер бібліотеки.
     */
    private final Map<UUID, Set<UUID>> glowingByCaster = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Виявлення зла";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return "§fПідсвічує темних і нежить поруч §c§lчервоним§f. §7Радіус: " + range + " бл.";
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (!context.playerData().isOnline(casterId)) return;

        tickCounter++;
        if (tickCounter % SCAN_PERIOD_TICKS != 0) return;

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        Set<UUID> previouslyGlowing = glowingByCaster.computeIfAbsent(casterId, k -> ConcurrentHashMap.newKeySet());
        Set<UUID> currentlyDetected = new HashSet<>();

        List<LivingEntity> nearby = context.targeting().getNearbyEntities(range);
        for (LivingEntity entity : nearby) {
            if (!HolyTargetClassifier.isDarkOrUndead(entity, context)) continue;

            UUID entityId = entity.getUniqueId();
            currentlyDetected.add(entityId);
            if (previouslyGlowing.add(entityId)) {
                context.glowing().setGlowing(entityId, casterId, ChatColor.RED);
            }
        }

        previouslyGlowing.removeIf(entityId -> {
            if (currentlyDetected.contains(entityId)) return false;
            context.glowing().removeGlowing(casterId, entityId);
            return true;
        });
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Set<UUID> previouslyGlowing = glowingByCaster.remove(casterId);
        if (previouslyGlowing == null) return;
        for (UUID entityId : previouslyGlowing) {
            context.glowing().removeGlowing(casterId, entityId);
        }
    }
}
