package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Recognition extends ToggleablePassiveAbility {
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
            graceTicks = GRACE_PERIOD_TICKS;
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
        return "[Пасивна] Ідентифікація";
    }

    @Override
    public String getDescription(Sequence userSequence) {
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
        return AbilityResult.success();
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        observations.put(casterId, new ObservationState());
        markedTargets.put(casterId, ConcurrentHashMap.newKeySet());
        markDurations.put(casterId, new ConcurrentHashMap<>());
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        removeAllMarks(casterId, context);
        observations.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        ObservationState state = observations.get(casterId);

        if (state == null) return;

        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isOnline()) return;

        // Оновлюємо таймери підсвітки
        updateMarkDurations(casterId, context);

        if (state.isInCooldown()) {
            state.decrementCooldown();
            return;
        }

        // --- ВИПРАВЛЕННЯ ПОМИЛКИ ---
        // Якщо ціль, за якою ми стежили, втекла в інший світ -> скидаємо стеження
        if (state.isObserving() && !state.target.getWorld().getUID().equals(caster.getWorld().getUID())) {
            state.reset();
        }

        Optional<LivingEntity> targetOpt;
        try {
            // Безпечний виклик пошуку цілі.
            // Якщо гравець переміщується між світами, rayTraceEntities може викинути IllegalArgumentException
            targetOpt = context.getTargetedEntity(OBSERVATION_RANGE);
        } catch (IllegalArgumentException e) {
            // Ігноруємо цей тік, поки світ не синхронізується
            return;
        }

        if (!targetOpt.isPresent()) {
            handleNoTarget(state);
            return;
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        // Перевірка світів перед подальшими діями
        if (!target.getWorld().getUID().equals(caster.getWorld().getUID())) {
            handleNoTarget(state);
            return;
        }

        if (isTargetMarked(casterId, targetId)) {
            state.reset();
            return;
        }

        if (!caster.hasLineOfSight(target)) {
            handleLostLineOfSight(state);
            return;
        }

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
        if (state.targetId == null || !state.targetId.equals(targetId)) {
            state.setTarget(target);
            return;
        }

        state.incrementProgress();

        if (state.isComplete()) {
            markTarget(casterId, target, context);
            state.startCooldown();
        }
    }

    private void markTarget(UUID casterId, LivingEntity target, IAbilityContext context) {
        UUID targetId = target.getUniqueId();

        Set<UUID> marks = markedTargets.get(casterId);
        Map<UUID, Integer> durations = markDurations.get(casterId);

        if (marks == null || durations == null) return;

        marks.add(targetId);
        durations.put(targetId, GLOW_DURATION_TICKS);

        ChatColor color = getHealthColor(target);
        context.setGlowing(targetId, color, GLOW_DURATION_TICKS);

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

    private void updateMarkDurations(UUID casterId, IAbilityContext context) {
        Map<UUID, Integer> durations = markDurations.get(casterId);
        if (durations == null || durations.isEmpty()) return;

        Set<UUID> expired = new HashSet<>();

        for (Map.Entry<UUID, Integer> entry : durations.entrySet()) {
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }

        for (UUID targetId : expired) {
            unmarkTarget(casterId, targetId, context);
        }
    }

    private void unmarkTarget(UUID casterId, UUID targetId, IAbilityContext context) {
        Set<UUID> marks = markedTargets.get(casterId);
        if (marks != null) marks.remove(targetId);

        Map<UUID, Integer> durations = markDurations.get(casterId);
        if (durations != null) durations.remove(targetId);

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
        if (durations != null) durations.clear();

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
        if (entity instanceof Player) return entity.getName();
        if (entity.getCustomName() != null) return entity.getCustomName();
        return entity.getType().name();
    }

    @Override
    public void cleanUp() {
        observations.clear();
        markedTargets.clear();
        markDurations.clear();
    }
}