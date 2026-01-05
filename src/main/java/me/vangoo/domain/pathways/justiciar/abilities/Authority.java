package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class Authority extends ActiveAbility {

    private static final double RADIUS = 10.0;
    private static final int MOB_DAMAGE = 6; // 3 hearts
    private static final int DEBUFF_DURATION_TICKS = 100; // 5 seconds
    private static final int COMBAT_CHECK_INTERVAL_TICKS = 100; // 5 seconds
    private static final double DAMAGE_REDUCTION_PER_STACK = 0.02; // 2%
    private static final int MAX_STACKS = 7; // 14% max reduction (7 * 2%)
    private static final int COOLDOWN = 40;
    private static final int COST = 50;

    // Tracking combat state for each affected player
    private static final Map<UUID, CombatState> combatStates = new ConcurrentHashMap<>();

    private static class CombatState {
        int stacks;
        long lastCombatTime;
        UUID casterUuid;

        CombatState(UUID casterUuid) {
            this.stacks = 0;
            this.lastCombatTime = System.currentTimeMillis();
            this.casterUuid = casterUuid;
        }

        void addStack() {
            if (stacks < MAX_STACKS) {
                stacks++;
            }
            lastCombatTime = System.currentTimeMillis();
        }

        void resetCombat() {
            lastCombatTime = System.currentTimeMillis();
        }

        boolean isInCombat() {
            // Consider in combat if less than 10 seconds since last combat
            return System.currentTimeMillis() - lastCombatTime < 10000;
        }

        double getDamageMultiplier() {
            return 1.0 - (stacks * DAMAGE_REDUCTION_PER_STACK);
        }
    }

    @Override
    public String getName() {
        return "Авторитет";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Випромінюєш ауру відчаю, що впливає на волю ворогів.\n" +
                ChatColor.GRAY + "▪ Радіус: " + ChatColor.WHITE + (int)RADIUS + " блоків\n" +
                ChatColor.GRAY + "▪ Моби: " + ChatColor.RED + (MOB_DAMAGE/2) + " ♥\n" +
                ChatColor.GRAY + "▪ Гравці: Дебаф -2% шкоди кожні 5с (макс 14%)";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        List<LivingEntity> nearbyEntities = context.getNearbyEntities(RADIUS);

        // Check if there's anyone nearby
        if (nearbyEntities.isEmpty()) {
            return AbilityResult.failure("В радіусі нікого немає!");
        }

        int playersAffected = 0;
        int mobsAffected = 0;

        // Process all nearby entities
        for (LivingEntity entity : nearbyEntities) {
            if (entity instanceof Player player) {
                // Apply debuff to player
                applyPlayerDebuff(context, player);
                playersAffected++;
            } else if (entity instanceof Monster) {
                // Damage mobs
                context.damage(entity.getUniqueId(), MOB_DAMAGE);

                // Visual effect for damaged mob
                context.spawnParticle(
                        Particle.SMOKE,
                        entity.getLocation().add(0, 1, 0),
                        15,
                        0.3, 0.5, 0.3
                );

                mobsAffected++;
            }
        }

        // Visual effects at caster location
        showAuraActivationEffects(context);

        // Build result message
        StringBuilder message = new StringBuilder(ChatColor.DARK_PURPLE + "Аура Відчаю активована!");
        if (playersAffected > 0) {
            message.append("\n").append(ChatColor.GRAY)
                    .append("Гравців уражено: ").append(ChatColor.YELLOW).append(playersAffected);
        }
        if (mobsAffected > 0) {
            message.append("\n").append(ChatColor.GRAY)
                    .append("Мобів пошкоджено: ").append(ChatColor.RED).append(mobsAffected);
        }

        return AbilityResult.successWithMessage(message.toString());
    }

    /**
     * Apply debuff to player and setup combat tracking
     */
    private void applyPlayerDebuff(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        UUID casterId = context.getCasterId();

        // Initialize or update combat state
        CombatState state = combatStates.computeIfAbsent(targetId, k -> new CombatState(casterId));
        state.casterUuid = casterId; // Update caster if different

        // Apply initial visual debuff
        context.applyEffect(
                targetId,
                PotionEffectType.SLOWNESS,
                DEBUFF_DURATION_TICKS,
                0
        );

        // Subtle darkness effect
        context.applyEffect(
                targetId,
                PotionEffectType.BLINDNESS,
                20, // 1 second
                0
        );

        // Setup combat tracking scheduler
        setupCombatTracking(context, targetId, casterId);

        // Visual effect on target
        context.spawnParticle(
                Particle.SQUID_INK,
                target.getLocation().add(0, 1, 0),
                20,
                0.3, 0.5, 0.3
        );

        // Sound for target
        context.playSound(
                target.getLocation(),
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                0.5f,
                0.8f
        );

        // Message to target
        context.sendMessage(targetId, ChatColor.DARK_GRAY + "Відчай огортає вашу свідомість...");
    }

    /**
     * Setup combat tracking for gradual damage reduction
     */
    private void setupCombatTracking(IAbilityContext context, UUID targetId, UUID casterId) {
        // Subscribe to damage events for this player
        context.subscribeToEvent(
                EntityDamageByEntityEvent.class,
                event -> {
                    // Check if this player is dealing damage
                    if (event.getDamager().getUniqueId().equals(targetId)) {
                        CombatState state = combatStates.get(targetId);
                        if (state != null) {
                            // Update combat time
                            state.resetCombat();
                            return true; // Target is involved in combat
                        }
                    }

                    // Check if this player is taking damage from caster
                    if (event.getEntity().getUniqueId().equals(targetId) &&
                            event.getDamager().getUniqueId().equals(casterId)) {
                        CombatState state = combatStates.get(targetId);
                        if (state != null) {
                            state.resetCombat();
                            return true;
                        }
                    }

                    return false;
                },
                event -> {
                    // Check if target is dealing damage to caster or vice versa
                    boolean targetIsDamager = event.getDamager().getUniqueId().equals(targetId);
                    boolean targetIsVictim = event.getEntity().getUniqueId().equals(targetId);
                    boolean casterInvolved = event.getDamager().getUniqueId().equals(casterId) ||
                            event.getEntity().getUniqueId().equals(casterId);

                    if ((targetIsDamager || targetIsVictim) && casterInvolved) {
                        CombatState state = combatStates.get(targetId);
                        if (state != null && targetIsDamager) {
                            // Apply damage reduction
                            double multiplier = state.getDamageMultiplier();
                            double originalDamage = event.getDamage();
                            double newDamage = originalDamage * multiplier;
                            event.setDamage(newDamage);

                            // Show visual feedback if significant reduction
                            if (state.stacks > 0) {
                                context.spawnParticle(
                                        Particle.SMOKE,
                                        event.getEntity().getLocation().add(0, 1, 0),
                                        5,
                                        0.2, 0.2, 0.2
                                );
                            }
                        }
                    }
                },
                DEBUFF_DURATION_TICKS
        );

        // Setup periodic stack addition
        scheduleStackIncrease(context, targetId, casterId);
    }
    private void scheduleStackIncrease(IAbilityContext context, UUID targetId, UUID casterId) {
        // First stack after 5 seconds
        context.scheduleDelayed(() -> {
            CombatState state = combatStates.get(targetId);
            if (state != null && state.isInCombat()) {
                state.addStack();

                // Notify player of increased despair
                context.sendMessage(
                        targetId,
                        ChatColor.DARK_GRAY + "Відчай посилюється... " +
                                ChatColor.RED + "(-" + (state.stacks * 2) + "% шкоди)"
                );

                // Visual effect
                Player target = context.getCaster().getServer().getPlayer(targetId);
                if (target != null) {
                    context.spawnParticle(
                            Particle.SQUID_INK,
                            target.getLocation().add(0, 1, 0),
                            10,
                            0.2, 0.3, 0.2
                    );
                }

                // Schedule next stack if not at max
                if (state.stacks < MAX_STACKS) {
                    scheduleStackIncrease(context, targetId, casterId);
                }
            } else {
                // Combat ended, cleanup
                cleanupCombatState(targetId);
            }
        }, COMBAT_CHECK_INTERVAL_TICKS);
    }

    /**
     * Cleanup combat state when debuff ends
     */
    private static void cleanupCombatState(UUID targetId) {
        combatStates.remove(targetId);
    }

    /**
     * Show visual effects for aura activation
     */
    private void showAuraActivationEffects(IAbilityContext context) {
        // Dark sphere expanding from caster
        context.playSphereEffect(
                context.getCasterLocation().add(0, 1, 0),
                RADIUS,
                Particle.SQUID_INK,
                40
        );

        // Dark wave
        context.playWaveEffect(
                context.getCasterLocation(),
                RADIUS,
                Particle.SMOKE,
                20
        );

        // Sound effects
        context.playSoundToCaster(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.6f);
        context.playSoundToCaster(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.8f);

        // Circle effect at ground level
        context.playCircleEffect(
                context.getCasterLocation(),
                RADIUS,
                Particle.ASH,
                60
        );
    }

    @Override
    public void cleanUp() {
        // Clean up all combat states when ability is removed
        combatStates.clear();
    }
}