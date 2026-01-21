package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permanent Passive: Arbiter's Gaze (Погляд Судді)
 *
 * Sequence 9-7: Базова здібність - погляд накладає Слабкість після 4 секунд
 * Sequence 6+: Додатково - інші гравці не можуть дивитися на Судді (примусово відводять погляд)
 */
public class ArbitersGaze extends PermanentPassiveAbility {

    private static final int STARE_THRESHOLD_SECONDS = 4;
    private static final int STARE_THRESHOLD_TICKS = STARE_THRESHOLD_SECONDS * 20;
    private static final int EFFECT_DURATION_TICKS = 10 * 20;
    private static final double MAX_DISTANCE = 15.0;

    // Параметри для примусового відведення погляду (Sequence 6+)
    private static final double GAZE_AVERSION_DISTANCE = 20.0;
    private static final int GAZE_AVERSION_THRESHOLD_SECONDS = 10;
    private static final int GAZE_AVERSION_THRESHOLD_TICKS = GAZE_AVERSION_THRESHOLD_SECONDS * 20;
    private static final int GAZE_AVERSION_COOLDOWN = 100;
    private static final float LOOK_AWAY_ANGLE = 45.0f;

    private final Map<UUID, StareState> stareStates = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, GazeAccumulator>> gazeAccumulators = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Погляд Судді";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        StringBuilder desc = new StringBuilder();
        desc.append("Ваш пильний погляд тисне на ворогів.\n");
        desc.append(ChatColor.GRAY).append("▪ ").append(ChatColor.WHITE)
                .append("Погляд ").append(STARE_THRESHOLD_SECONDS).append(" сек → Слабкість\n");

        if (userSequence.level() <= 6) {
            desc.append(ChatColor.GOLD).append("▪ ").append(ChatColor.YELLOW)
                    .append("Примусове відведення погляду\n")
                    .append(ChatColor.GRAY).append("  Інші не можуть дивитися на вас");
        }

        return desc.toString();
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        stareStates.put(casterId, new StareState());
        gazeAccumulators.put(casterId, new ConcurrentHashMap<>());
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        stareStates.remove(casterId);
        gazeAccumulators.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        StareState state = stareStates.computeIfAbsent(casterId, k -> new StareState());

        Beyonder casterBeyonder = context.beyonder().getBeyonder(casterId);
        int casterSequence = casterBeyonder.getSequenceLevel();

        // === ЧАСТИНА 1: Погляд Арбітра на ціль (оригінальна механіка) ===
        processArbiterStare(context, casterId, state);

        // === ЧАСТИНА 2: Примусове відведення погляду (Sequence 6+) ===
        if (casterSequence <= 6) {
            processGazeAversion(context, casterId, casterSequence);
        }
    }

    /**
     * Оригінальна механіка: Арбітр дивиться на ціль
     */
    private void processArbiterStare(IAbilityContext context, UUID casterId, StareState state) {
        Location eyeLocation = context.playerData().getEyeLocation(casterId);
        if (eyeLocation == null || eyeLocation.getWorld() == null) return;

        Vector direction = eyeLocation.getDirection();
        World world = eyeLocation.getWorld();

        // RayTrace для пошуку цілі
        RayTraceResult result = world.rayTrace(
                eyeLocation,
                direction,
                MAX_DISTANCE,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                entity -> entity instanceof Player && !entity.getUniqueId().equals(casterId)
        );

        UUID targetId = null;
        if (result != null && result.getHitEntity() instanceof Player target) {
            targetId = target.getUniqueId();
        }

        if (targetId != null) {
            processStare(context, casterId, targetId, state);
        } else {
            state.reset();
        }
    }

    /**
     * Нова механіка: Примусове відведення погляду інших гравців (Sequence 6+)
     */
    private void processGazeAversion(IAbilityContext context, UUID arbiterId, int arbiterSequence) {
        Map<UUID, GazeAccumulator> accumulators = gazeAccumulators.get(arbiterId);
        if (accumulators == null) return;

        long currentTime = System.currentTimeMillis();
        Location arbiterLocation = context.playerData().getCurrentLocation(arbiterId);
        if (arbiterLocation == null) return;

        // Перевіряємо всіх гравців поблизу
        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(GAZE_AVERSION_DISTANCE);

        for (Player otherPlayer : nearbyPlayers) {
            UUID otherId = otherPlayer.getUniqueId();
            if (otherId.equals(arbiterId)) continue;
            if (otherPlayer.getGameMode() == GameMode.SPECTATOR) continue;

            GazeAccumulator accumulator = accumulators.computeIfAbsent(otherId, k -> new GazeAccumulator());

            // Перевіряємо чи інший гравець дивиться на Арбітра
            if (isLookingAt(otherPlayer, arbiterId, context)) {
                if (accumulator.isOnCooldown(currentTime)) {
                    continue;
                }

                accumulator.ticksAccumulated++;

                // Візуальні попередження
                if (accumulator.ticksAccumulated % 40 == 0 &&
                        accumulator.ticksAccumulated < GAZE_AVERSION_THRESHOLD_TICKS) {
                    int secondsLeft = (GAZE_AVERSION_THRESHOLD_TICKS - accumulator.ticksAccumulated) / 20;

                    String arbiterName = context.playerData().getName(arbiterId);
                    context.messaging().sendMessageToActionBar(otherId,
                            Component.text("⚠ ", NamedTextColor.GOLD)
                                    .append(Component.text("Не дивіться на " + arbiterName, NamedTextColor.GRAY))
                                    .append(Component.text(" (" + secondsLeft + "с)", NamedTextColor.DARK_GRAY))
                    );

                    Location otherLocation = context.playerData().getCurrentLocation(otherId);
                    if (otherLocation != null) {
                        context.effects().playSound(otherLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                    }
                }

                // Перевірка порогу
                if (accumulator.ticksAccumulated >= GAZE_AVERSION_THRESHOLD_TICKS) {
                    if (!canResistGazeAversion(context, arbiterSequence, otherId)) {
                        forceLookAway(otherId, arbiterId, context);
                        accumulator.reset(currentTime);

                        String otherName = context.playerData().getName(otherId);
                        context.messaging().sendMessageToActionBar(arbiterId,
                                Component.text("✦ ", NamedTextColor.GOLD)
                                        .append(Component.text(otherName + " більше не може дивитися на вас",
                                                NamedTextColor.GRAY))
                        );
                    } else {
                        accumulator.reset(currentTime);

                        String otherName = context.playerData().getName(otherId);
                        context.messaging().sendMessageToActionBar(arbiterId,
                                Component.text(otherName + " чинить опір вашому погляду",
                                        NamedTextColor.GRAY)
                        );

                        Location otherEyeLocation = context.playerData().getEyeLocation(otherId);
                        if (otherEyeLocation != null) {
                            context.effects().spawnParticle(Particle.FIREWORK, otherEyeLocation, 10,
                                    0.3, 0.3, 0.3);
                            context.effects().playSound(otherEyeLocation, Sound.ITEM_SHIELD_BLOCK, 0.5f, 1.2f);
                        }
                    }
                }
            } else {
                accumulator.ticksAccumulated = 0;
            }
        }

        // Очищуємо акумулятори офлайн гравців
        accumulators.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            if (!context.playerData().isOnline(playerId)) {
                return true;
            }
            Location playerLoc = context.playerData().getCurrentLocation(playerId);
            return playerLoc == null || playerLoc.distance(arbiterLocation) > GAZE_AVERSION_DISTANCE;
        });
    }

    /**
     * Перевіряє чи один гравець дивиться на іншого
     */
    private boolean isLookingAt(Player observer, UUID targetId, IAbilityContext context) {
        Location observerEye = observer.getEyeLocation();
        Location targetEye = context.playerData().getEyeLocation(targetId);

        if (targetEye == null) return false;

        Vector toTarget = targetEye.toVector().subtract(observerEye.toVector()).normalize();
        Vector observerDirection = observerEye.getDirection().normalize();

        double dotProduct = observerDirection.dot(toTarget);
        double angleThreshold = Math.cos(Math.toRadians(30));

        if (dotProduct < angleThreshold) {
            return false;
        }

        // Перевіряємо перешкоди
        RayTraceResult result = observerEye.getWorld().rayTrace(
                observerEye,
                observerDirection,
                GAZE_AVERSION_DISTANCE,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                entity -> entity.getUniqueId().equals(targetId)
        );

        return result != null && result.getHitEntity() != null;
    }

    /**
     * Перевіряє чи може ціль опиратися
     */
    private boolean canResistGazeAversion(IAbilityContext context, int arbiterSequence, UUID targetId) {
        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
        if (targetBeyonder == null) return false;

        int targetSequence = targetBeyonder.getSequenceLevel();
        SequenceBasedSuccessChance resistChance =
                new SequenceBasedSuccessChance(targetSequence, arbiterSequence);

        return resistChance.rollSuccess();
    }

    /**
     * Примусово відводить погляд гравця
     */
    private void forceLookAway(UUID targetId, UUID arbiterId, IAbilityContext context) {
        Location targetLoc = context.playerData().getEyeLocation(targetId);
        Location arbiterLoc = context.playerData().getEyeLocation(arbiterId);

        if (targetLoc == null || arbiterLoc == null) return;

        Vector toArbiter = arbiterLoc.toVector().subtract(targetLoc.toVector()).normalize();
        Vector lookAwayDirection = toArbiter.getCrossProduct(new Vector(0, 1, 0)).normalize();

        if (lookAwayDirection.lengthSquared() < 0.01) {
            lookAwayDirection = toArbiter.getCrossProduct(new Vector(1, 0, 0)).normalize();
        }

        Vector currentDirection = targetLoc.getDirection();
        double angle = Math.toRadians(LOOK_AWAY_ANGLE);
        Vector newDirection = currentDirection.clone()
                .add(lookAwayDirection.multiply(Math.sin(angle)))
                .normalize();

        Location newLook = targetLoc.clone().setDirection(newDirection);
        context.entity().teleport(targetId, newLook);

        // Візуальні ефекти
        context.effects().spawnParticle(Particle.ENCHANT, targetLoc, 10, 0.2, 0.2, 0.2);
        context.effects().playSound(targetLoc, Sound.ENTITY_ENDERMAN_STARE, 0.3f, 1.5f);

        String arbiterName = context.playerData().getName(arbiterId);
        context.messaging().sendMessageToActionBar(targetId,
                Component.text("Ви не можете дивитися на ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(arbiterName, NamedTextColor.GOLD))
        );
    }

    /**
     * Обробка базової механіки погляду
     */
    private void processStare(IAbilityContext context, UUID casterId, UUID targetId, StareState state) {
        if (targetId.equals(state.targetId)) {
            state.ticksAccumulated++;

            // Візуальний ефект накопичення
            if (state.ticksAccumulated % 5 == 0) {
                Location targetEye = context.playerData().getEyeLocation(targetId);
                if (targetEye != null) {
                    context.effects().spawnParticle(Particle.ENCHANT,
                            targetEye.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1);
                }
            }

            // Перевірка порогу
            if (state.ticksAccumulated >= STARE_THRESHOLD_TICKS) {
                applyDebuff(context, casterId, targetId);
                state.reset();
            }
        } else {
            state.targetId = targetId;
            state.ticksAccumulated = 0;
        }
    }

    /**
     * Накладає дебаф Слабкість на ціль
     */
    private void applyDebuff(IAbilityContext context, UUID casterId, UUID targetId) {
        Beyonder casterBeyonder = context.beyonder().getBeyonder(casterId);
        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);

        int casterSequence = casterBeyonder.getSequenceLevel();

        if (targetBeyonder == null) {
            applyWeaknessEffect(targetId, casterId, context);
            return;
        }

        int targetSequence = targetBeyonder.getSequenceLevel();
        SequenceBasedSuccessChance successChance =
                new SequenceBasedSuccessChance(casterSequence, targetSequence);

        if (successChance.rollSuccess()) {
            applyWeaknessEffect(targetId, casterId, context);
        } else {
            // Ціль опиралася
            Location targetEye = context.playerData().getEyeLocation(targetId);
            if (targetEye != null) {
                context.effects().spawnParticle(Particle.FIREWORK, targetEye, 15, 0.3, 0.5, 0.3);
                context.effects().playSound(targetEye, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
            }

            String targetName = context.playerData().getName(targetId);
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text(targetName + " опирається вашому погляду ", NamedTextColor.GRAY)
                            .append(Component.text("(" + successChance.getFormattedChance() + ")",
                                    NamedTextColor.DARK_GRAY))
            );

            context.messaging().sendMessageToActionBar(targetId,
                    Component.text("Ви опиралися Погляду Судді!", NamedTextColor.GREEN)
            );
        }
    }

    private void applyWeaknessEffect(UUID targetId, UUID casterId, IAbilityContext context) {
        context.entity().applyPotionEffect(targetId, PotionEffectType.WEAKNESS, EFFECT_DURATION_TICKS, 0);

        Location targetLocation = context.playerData().getCurrentLocation(targetId);
        Location casterLocation = context.playerData().getCurrentLocation(casterId);

        if (targetLocation != null) {
            context.effects().playSound(targetLocation, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

            Location targetEye = context.playerData().getEyeLocation(targetId);
            if (targetEye != null) {
                context.effects().spawnParticle(Particle.CRIT, targetEye, 15, 0.3, 0.5, 0.3);
            }
            context.effects().spawnParticle(Particle.LARGE_SMOKE, targetLocation, 5, 0.2, 1.0, 0.2);
        }

        if (casterLocation != null) {
            context.effects().playSound(casterLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }

        String targetName = context.playerData().getName(targetId);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Придушили волю ", NamedTextColor.GOLD)
                        .append(Component.text(targetName, NamedTextColor.RED))
        );

        context.messaging().sendMessageToActionBar(targetId,
                Component.text("Ваші рухи слабшають під пильним поглядом...", NamedTextColor.GRAY)
        );
    }

    @Override
    public void cleanUp() {
        stareStates.clear();
        gazeAccumulators.clear();
    }

    private static class StareState {
        UUID targetId = null;
        int ticksAccumulated = 0;

        void reset() {
            targetId = null;
            ticksAccumulated = 0;
        }
    }

    private static class GazeAccumulator {
        int ticksAccumulated = 0;
        long cooldownUntil = 0;

        void reset(long currentTime) {
            ticksAccumulated = 0;
            cooldownUntil = currentTime + (GAZE_AVERSION_COOLDOWN * 50);
        }

        boolean isOnCooldown(long currentTime) {
            return currentTime < cooldownUntil;
        }
    }
}