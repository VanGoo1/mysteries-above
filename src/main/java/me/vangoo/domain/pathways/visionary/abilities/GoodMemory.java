package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GoodMemory extends ToggleablePassiveAbility {
    // Configuration
    private static final int OBSERVE_DURATION_TICKS = 2 * 20; // 2 seconds
    private static final int GLOW_DURATION_TICKS = 20 * 20; // 20 seconds
    private static final int OBSERVATION_RANGE = 30;
    private static final int GRACE_PERIOD_TICKS = 5; // 0.25s tolerance for lag
    private static final int POST_ACTIVATION_COOLDOWN = 10; // 0.5s cooldown after marking target

    // Per-caster state
    private final Map<UUID, ObservationState> observations = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> markedTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> markDurations = new ConcurrentHashMap<>();

    /**
     * Tracks observation progress for a single caster
     */
    private static class ObservationState {
        private LivingEntity target;
        private UUID targetId;
        private int progressTicks;
        private int graceTicks;
        private int cooldownTicks;

        boolean isObserving() {
            return target != null && !isInCooldown();
        }

        boolean isInCooldown() {
            return cooldownTicks > 0;
        }

        void reset() {
            target = null;
            targetId = null;
            progressTicks = 0;
            graceTicks = 0;
            // keep cooldownTicks as-is (cooldown preserved until it naturally expires)
        }

        void startCooldown() {
            progressTicks = 0;
            graceTicks = GRACE_PERIOD_TICKS;
            cooldownTicks = POST_ACTIVATION_COOLDOWN;
        }

        void decrementCooldown() {
            if (cooldownTicks > 0) {
                cooldownTicks--;
            }
        }

        void decrementGrace() {
            if (graceTicks > 0) {
                graceTicks--;
            }
        }

        void setTarget(LivingEntity newTarget) {
            this.target = newTarget;
            this.targetId = newTarget.getUniqueId();
            this.progressTicks = 0;
            this.graceTicks = GRACE_PERIOD_TICKS;
        }

        void incrementProgress() {
            progressTicks++;
            graceTicks = GRACE_PERIOD_TICKS; // Reset grace on successful observation
        }

        int getProgressPercentage() {
            return (progressTicks * 100) / OBSERVE_DURATION_TICKS;
        }

        boolean isComplete() {
            return progressTicks >= OBSERVE_DURATION_TICKS;
        }
    }

    // ==========================================
    // ABILITY METADATA
    // ==========================================

    @Override
    public String getName() {
        return "[Пасивна] Хороша пам'ять";
    }

    @Override
    public String getDescription() {
        return String.format(
                "Після %.1fс спостереження за мобом чи гравцем — ціль підсвічується на %dс в радіусі %d блоків. " +
                        "Увімкніть/вимкніть правою кнопкою миші.",
                OBSERVE_DURATION_TICKS / 20.0,
                GLOW_DURATION_TICKS / 20,
                OBSERVATION_RANGE
        );
    }

    // ==========================================
    // LIFECYCLE HOOKS
    // ==========================================

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // Toggle handled by PassiveAbilityManager (platform)
        return AbilityResult.success();
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        observations.put(casterId, new ObservationState());
        markedTargets.put(casterId, ConcurrentHashMap.newKeySet());
        markDurations.put(casterId, new ConcurrentHashMap<>());

        context.sendMessageToCaster(
                ChatColor.GREEN + "Хороша пам'ять " +
                        ChatColor.GRAY + "увімкнена. Спостерігайте за ціллю " +
                        (OBSERVE_DURATION_TICKS / 20.0) + "с."
        );
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        removeAllMarks(casterId, context);
        observations.remove(casterId);

        context.sendMessageToCaster(
                ChatColor.YELLOW + "Хороша пам'ять " +
                        ChatColor.GRAY + "вимкнена."
        );
    }

    /**
     * Main tick called by platform every server tick while ability enabled.
     * Бизнес-логика: уменьшает марк-таймеры в начале, затем работает с наблюдением.
     */
    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        ObservationState state = observations.get(casterId);

        if (state == null) {
            return;
        }

        Player caster = context.getCaster();
        if (caster == null || !caster.isOnline()) {
            return;
        }

        // --- ВАЖНО: обновляем время пометок каждый тик, независимо от налаживания прицела ---
        updateMarkDurations(casterId, context);

        // Handle post-activation cooldown
        if (state.isInCooldown()) {
            state.decrementCooldown();
            return;
        }

        // Try to find target
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(OBSERVATION_RANGE);

        if (!targetOpt.isPresent()) {
            handleNoTarget(state);
            return;
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        // Skip if already marked
        if (isTargetMarked(casterId, targetId)) {
            // keep state reset to avoid re-accumulating progress on already marked target
            state.reset();
            return;
        }

        // Check line of sight
        if (!caster.hasLineOfSight(target)) {
            handleLostLineOfSight(state);
            return;
        }

        // Valid target - process observation
        processObservation(state, target, targetId, casterId, context);
    }

    // ==========================================
    // OBSERVATION LOGIC
    // ==========================================

    private void handleNoTarget(ObservationState state) {
        if (state.graceTicks > 0) {
            state.decrementGrace();
        } else {
            state.reset();
        }
    }

    private void handleLostLineOfSight(ObservationState state) {
        if (state.graceTicks > 0) {
            state.decrementGrace();
        } else {
            state.reset();
        }
    }

    private void processObservation(ObservationState state, LivingEntity target, UUID targetId,
                                    UUID casterId, IAbilityContext context) {
        // If started observing a new target -> set it and wait for next ticks to accumulate
        if (state.targetId == null || !state.targetId.equals(targetId)) {
            state.setTarget(target);
            return;
        }

        // Continue observing same target
        state.incrementProgress();

        // Check if observation complete
        if (state.isComplete()) {
            markTarget(casterId, target, context);
            state.startCooldown();
        }
    }

    private void markTarget(UUID casterId, LivingEntity target, IAbilityContext context) {
        UUID targetId = target.getUniqueId();

        Set<UUID> marks = markedTargets.get(casterId);
        Map<UUID, Integer> durations = markDurations.get(casterId);

        if (marks == null || durations == null) {
            return;
        }

        // add mark and initialise duration counter
        marks.add(targetId);
        durations.put(targetId, GLOW_DURATION_TICKS);

        // apply glowing via context (контекст не должен сам удалять glowing по таймеру)
        ChatColor color = getHealthColor(target);
        context.setGlowing(targetId, color, GLOW_DURATION_TICKS);

        // notify caster
        String healthInfo = String.format("[HP: %d%%]", getHealthPercentage(target));
        context.sendMessage(
                casterId,
                ChatColor.GREEN + "✓ Запам'ятано: " +
                        ChatColor.WHITE + getEntityName(target) + " " +
                        ChatColor.GREEN + healthInfo +
                        " [" + (GLOW_DURATION_TICKS / 20) + "с]"
        );

        context.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
    }

    /**
     * Decrement all mark durations for this caster once per tick.
     * When duration reaches zero — unmark target (and remove glow via context).
     */
    private void updateMarkDurations(UUID casterId, IAbilityContext context) {
        Map<UUID, Integer> durations = markDurations.get(casterId);
        if (durations == null || durations.isEmpty()) {
            return;
        }

        Set<UUID> expired = new HashSet<>();

        // Decrement remaining ticks
        for (Map.Entry<UUID, Integer> entry : durations.entrySet()) {
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }

        // Unmark expired (this will remove from markedTargets and durations, and remove glowing)
        for (UUID targetId : expired) {
            unmarkTarget(casterId, targetId, context);
        }
    }

    private void unmarkTarget(UUID casterId, UUID targetId, IAbilityContext context) {
        Set<UUID> marks = markedTargets.get(casterId);
        if (marks != null) {
            marks.remove(targetId);
        }

        Map<UUID, Integer> durations = markDurations.get(casterId);
        if (durations != null) {
            durations.remove(targetId);
        }

        context.removeGlowing(targetId);
    }

    private void removeAllMarks(UUID casterId, IAbilityContext context) {
        Set<UUID> marks = markedTargets.get(casterId);
        if (marks != null) {
            for (UUID targetId : new HashSet<>(marks)) {
                context.removeGlowing(targetId);
            }
            marks.clear();
        }

        Map<UUID, Integer> durations = markDurations.get(casterId);
        if (durations != null) {
            durations.clear();
        }

        markedTargets.remove(casterId);
        markDurations.remove(casterId);
    }

    private boolean isTargetMarked(UUID casterId, UUID targetId) {
        Set<UUID> marks = markedTargets.get(casterId);
        return marks != null && marks.contains(targetId);
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private ChatColor getHealthColor(LivingEntity target) {
        int percentage = getHealthPercentage(target);

        if (percentage > 75) return ChatColor.GREEN;
        if (percentage > 50) return ChatColor.YELLOW;
        if (percentage > 25) return ChatColor.GOLD;
        return ChatColor.RED;
    }

    private int getHealthPercentage(LivingEntity target) {
        double maxHealth = Objects.requireNonNull(
                target.getAttribute(Attribute.MAX_HEALTH)
        ).getValue();
        return (int) Math.round((target.getHealth() / maxHealth) * 100);
    }

    private String getEntityName(LivingEntity entity) {
        if (entity instanceof Player) {
            return entity.getName();
        }
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }
        return entity.getType().name();
    }

    @Override
    public void cleanUp() {
        observations.clear();
        markedTargets.clear();
        markDurations.clear();
    }
}
