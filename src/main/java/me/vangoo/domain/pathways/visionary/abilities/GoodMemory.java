package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GoodMemory extends Ability {
    private static final int OBSERVE_TICKS_REQUIRED = 3 * 20; // 3s
    private static final int GLOW_TICKS = 20 * 20; // 20s
    private static final int RANGE = 30;

    // Track which casters have this ability enabled
    private final Set<UUID> enabledCasters = ConcurrentHashMap.newKeySet();

    // Для відстеження підсвічених entities per caster
    private final Map<UUID, Set<UUID>> glowingEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> glowTicksRemaining = new ConcurrentHashMap<>();

    private static class ObservingState {
        LivingEntity current;
        int ticks;
    }

    // Store observing states per caster
    private final Map<UUID, ObservingState> observingStates = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "[Пасивна] Хороша пам'ять";
    }

    @Override
    public String getDescription() {
        return "Після " + OBSERVE_TICKS_REQUIRED / 20 + "с спостереження за мобом чи гравцем — ціль підсвічується на " +
                GLOW_TICKS / 20 + "с в радіусі " + RANGE + " блоків.";
    }

    @Override
    public int getSpiritualityCost() {
        return 0;
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        if (enabledCasters.contains(casterId)) {
            // Вимкнути
            enabledCasters.remove(casterId);
            observingStates.remove(casterId);

            // Видалити всі підсвічування для цього гравця
            removeAllGlowingForCaster(casterId, context);

            context.sendMessageToCaster(
                    ChatColor.YELLOW + "Хороша пам'ять" + ChatColor.GRAY + " вимкнена."
            );
        } else {
            // Увімкнути
            enabledCasters.add(casterId);
            observingStates.put(casterId, new ObservingState());
            glowingEntities.put(casterId, ConcurrentHashMap.newKeySet());
            glowTicksRemaining.put(casterId, new ConcurrentHashMap<>());

            context.sendMessageToCaster(
                    ChatColor.GREEN + "Хороша пам'ять" + ChatColor.GRAY + " увімкнена. Спостерігайте за ціллю " +
                            OBSERVE_TICKS_REQUIRED / 20 + "с."
            );
        }

        return AbilityResult.success();
    }

    public void tick(IAbilityContext context) {
        if (!enabledCasters.contains(context.getCasterId())) {
            return;
        }

        UUID casterId = context.getCasterId();
        ObservingState state = observingStates.get(casterId);
        if (state == null) return;

        Player caster = context.getCaster();
        if (caster == null || !caster.isOnline()) return;

        // Get target using context
        Optional<LivingEntity> targetedEntity = context.getTargetedEntity(RANGE);

        if (!targetedEntity.isPresent() || !caster.hasLineOfSight(targetedEntity.get())) {
            // Немає валідної цілі — скинути стан
            state.current = null;
            state.ticks = 0;
            return;
        }

        LivingEntity target = targetedEntity.get();

        if (state.current == null || !state.current.equals(target)) {
            state.current = target;
            state.ticks = 0;
        } else {
            state.ticks += 1;
        }

        if (state.ticks >= OBSERVE_TICKS_REQUIRED) {
            UUID targetId = target.getUniqueId();

            // Перевірити чи ціль вже підсвічена для цього гравця
            if (!glowingEntities.get(casterId).contains(targetId)) {
                addGlowing(casterId, target, context);

                String healthInfo = "[HP: " + getHealthPercentage(target) + "%]";
                context.sendMessage(
                        casterId,
                        ChatColor.GREEN + "Ви запам'ятали " + ChatColor.WHITE + getEntityName(target) +
                                ChatColor.GREEN + " " + healthInfo + ". Підсвічено на " + GLOW_TICKS / 20 + "с."
                );
            }

            // Після спрацьовування почати відлік знову
            state.ticks = 0;
        }

        // Update glow ticks
        updateGlowTicks(casterId, context);
    }

    private void updateGlowTicks(UUID casterId, IAbilityContext context) {
        Map<UUID, Integer> glowTicks = glowTicksRemaining.get(casterId);
        if (glowTicks == null) return;

        Set<UUID> entitiesToRemove = new HashSet<>();

        for (Map.Entry<UUID, Integer> entry : glowTicks.entrySet()) {
            UUID targetId = entry.getKey();
            int remainingTicks = entry.getValue() - 1;

            if (remainingTicks <= 0) {
                entitiesToRemove.add(targetId);
            } else {
                entry.setValue(remainingTicks);
            }
        }

        // Remove expired glowing entities
        for (UUID targetId : entitiesToRemove) {
            removeGlowing(casterId, targetId, context);
        }
    }

    private void addGlowing(UUID casterId, LivingEntity target, IAbilityContext context) {
        UUID targetId = target.getUniqueId();

        // Додати до списку підсвічених
        glowingEntities.get(casterId).add(targetId);
        glowTicksRemaining.get(casterId).put(targetId, GLOW_TICKS);

        // Визначити колір підсвічування на основі здоров'я
        ChatColor glowColor = getGlowColor(target);

        // Використати context для підсвічування
        context.setGlowing(targetId, glowColor, GLOW_TICKS);
    }

    private ChatColor getGlowColor(LivingEntity target) {
        int healthPercentage = getHealthPercentage(target);

        if (healthPercentage > 75) {
            return ChatColor.GREEN;
        } else if (healthPercentage > 50) {
            return ChatColor.YELLOW;
        } else if (healthPercentage > 25) {
            return ChatColor.GOLD;
        } else {
            return ChatColor.RED;
        }
    }

    private int getHealthPercentage(LivingEntity target) {
        double maxHealth = Objects.requireNonNull(
                target.getAttribute(Attribute.MAX_HEALTH)
        ).getValue();
        return (int) Math.round((target.getHealth() / maxHealth) * 100);
    }

    private void removeGlowing(UUID casterId, UUID targetId, IAbilityContext context) {
        Set<UUID> playerGlowing = glowingEntities.get(casterId);
        if (playerGlowing != null) {
            playerGlowing.remove(targetId);
        }

        Map<UUID, Integer> glowTicks = glowTicksRemaining.get(casterId);
        if (glowTicks != null) {
            glowTicks.remove(targetId);
        }

        // Використати context для видалення підсвічування
        context.removeGlowing(targetId);
    }

    private void removeAllGlowingForCaster(UUID casterId, IAbilityContext context) {
        Set<UUID> playerGlowing = glowingEntities.get(casterId);
        if (playerGlowing != null) {
            for (UUID targetId : playerGlowing) {
                context.removeGlowing(targetId);
            }
            playerGlowing.clear();
        }

        Map<UUID, Integer> glowTicks = glowTicksRemaining.get(casterId);
        if (glowTicks != null) {
            glowTicks.clear();
        }

        glowingEntities.remove(casterId);
        glowTicksRemaining.remove(casterId);
    }

    private String getEntityName(Entity e) {
        if (e instanceof Player) return e.getName();
        if (e.getCustomName() != null) return e.getCustomName();
        return e.getType().name();
    }

    @Override
    public int getCooldown() {
        return 0;
    }

    @Override
    public void cleanUp() {
        enabledCasters.clear();
        observingStates.clear();
        glowingEntities.clear();
        glowTicksRemaining.clear();
    }
}