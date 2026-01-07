package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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
    private static final int GAZE_AVERSION_THRESHOLD_SECONDS = 10; // 10 секунд дивитися перед відведенням
    private static final int GAZE_AVERSION_THRESHOLD_TICKS = GAZE_AVERSION_THRESHOLD_SECONDS * 20;
    private static final int GAZE_AVERSION_COOLDOWN = 100; // Кулдаун після відведення (в тіках)
    private static final float LOOK_AWAY_ANGLE = 45.0f; // Кут відведення погляду (в градусах)

    // Зберігає стан погляду для кожного Арбітра
    private final Map<UUID, StareState> stareStates = new ConcurrentHashMap<>();

    // Зберігає стан погляду інших гравців на Арбітра
    // Key: UUID Арбітра, Value: Map<UUID того хто дивиться, GazeAccumulator>
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
        UUID casterId = context.getCaster().getUniqueId();
        stareStates.put(casterId, new StareState());
        gazeAccumulators.put(casterId, new ConcurrentHashMap<>());
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCaster().getUniqueId();
        stareStates.remove(casterId);
        gazeAccumulators.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID casterId = caster.getUniqueId();

        StareState state = stareStates.computeIfAbsent(casterId, k -> new StareState());

        Beyonder casterBeyonder = context.getCasterBeyonder();
        int casterSequence = casterBeyonder.getSequenceLevel();

        // === ЧАСТИНА 1: Погляд Арбітра на ціль (оригінальна механіка) ===
        processArbiterStare(context, caster, state);

        // === ЧАСТИНА 2: Примусове відведення погляду (Sequence 6+) ===
        if (casterSequence <= 6) {
            processGazeAversion(context, caster, casterId, casterSequence);
        }
    }

    /**
     * Оригінальна механіка: Арбітр дивиться на ціль
     */
    private void processArbiterStare(IAbilityContext context, Player caster, StareState state) {
        // Виконуємо RayTrace для пошуку цілі
        RayTraceResult result = caster.getWorld().rayTrace(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                MAX_DISTANCE,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                entity -> entity instanceof Player && !entity.getUniqueId().equals(caster.getUniqueId())
        );

        Player targetedPlayer = null;
        if (result != null && result.getHitEntity() instanceof Player) {
            targetedPlayer = (Player) result.getHitEntity();
        }

        if (targetedPlayer != null) {
            processStare(context, caster, targetedPlayer, state);
        } else {
            state.reset();
        }
    }

    /**
     * Нова механіка: Примусове відведення погляду інших гравців (Sequence 6+)
     */
    private void processGazeAversion(IAbilityContext context, Player arbiter, UUID arbiterId, int arbiterSequence) {
        Map<UUID, GazeAccumulator> accumulators = gazeAccumulators.get(arbiterId);
        if (accumulators == null) return;

        long currentTime = System.currentTimeMillis();

        // Перевіряємо всіх гравців поблизу
        for (Player otherPlayer : context.getNearbyPlayers(GAZE_AVERSION_DISTANCE)) {
            if (otherPlayer.equals(arbiter)) continue;
            if (otherPlayer.getGameMode() == GameMode.SPECTATOR) continue;

            UUID otherId = otherPlayer.getUniqueId();
            GazeAccumulator accumulator = accumulators.computeIfAbsent(otherId, k -> new GazeAccumulator());

            // Перевіряємо чи інший гравець дивиться на Арбітра
            if (isLookingAt(otherPlayer, arbiter)) {
                // Якщо на кулдауні - пропускаємо
                if (accumulator.isOnCooldown(currentTime)) {
                    continue;
                }

                // Накопичуємо час погляду
                accumulator.ticksAccumulated++;

                // Візуальні попередження про накопичення (кожні 2 секунди)
                if (accumulator.ticksAccumulated % 40 == 0 && accumulator.ticksAccumulated < GAZE_AVERSION_THRESHOLD_TICKS) {
                    int secondsLeft = (GAZE_AVERSION_THRESHOLD_TICKS - accumulator.ticksAccumulated) / 20;

                    otherPlayer.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.GOLD + "⚠ " + ChatColor.GRAY +
                                    "Не дивіться на " + arbiter.getName() +
                                    ChatColor.DARK_GRAY + " (" + secondsLeft + "с)")
                    );

                    // Тихий звук попередження
                    otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                }

                // Перевірка порогу - якщо надивився 10 секунд
                if (accumulator.ticksAccumulated >= GAZE_AVERSION_THRESHOLD_TICKS) {
                    // Перевіряємо опір
                    if (!canResistGazeAversion(context, arbiterSequence, otherId)) {
                        // Примусово відводимо погляд
                        forceLookAway(otherPlayer, arbiter, context);

                        // Скидаємо лічильник та встановлюємо кулдаун
                        accumulator.reset(currentTime);

                        // Action bar повідомлення для Арбітра
                        arbiter.spigot().sendMessage(
                                ChatMessageType.ACTION_BAR,
                                new TextComponent(ChatColor.GOLD + "✦ " + otherPlayer.getName() +
                                        ChatColor.GRAY + " більше не може дивитися на вас")
                        );
                    } else {
                        // Ціль опиралася - скидаємо та даємо невеликий кулдаун
                        accumulator.reset(currentTime);

                        arbiter.spigot().sendMessage(
                                ChatMessageType.ACTION_BAR,
                                new TextComponent(ChatColor.GRAY + otherPlayer.getName() +
                                        " чинить опір вашому погляду")
                        );

                        // Візуальний ефект опору для цілі
                        otherPlayer.getWorld().spawnParticle(
                                Particle.FIREWORK,
                                otherPlayer.getEyeLocation(),
                                10,
                                0.3, 0.3, 0.3,
                                0.05
                        );
                        otherPlayer.playSound(otherPlayer.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.5f, 1.2f);
                    }
                }
            } else {
                // Не дивиться - скидаємо накопичення
                accumulator.ticksAccumulated = 0;
            }
        }

        // Очищуємо акумулятори гравців що вийшли з зони або офлайн
        accumulators.entrySet().removeIf(entry -> {
            Player p = context.getCaster().getServer().getPlayer(entry.getKey());
            return p == null || !p.isOnline() || p.getLocation().distance(arbiter.getLocation()) > GAZE_AVERSION_DISTANCE;
        });
    }

    /**
     * Перевіряє чи один гравець дивиться на іншого
     */
    private boolean isLookingAt(Player observer, Player target) {
        Location observerEye = observer.getEyeLocation();
        Vector toTarget = target.getEyeLocation().toVector().subtract(observerEye.toVector()).normalize();
        Vector observerDirection = observerEye.getDirection().normalize();

        // Перевіряємо кут між напрямком погляду та напрямком до цілі
        double dotProduct = observerDirection.dot(toTarget);
        double angleThreshold = Math.cos(Math.toRadians(30)); // 30 градусів конус

        if (dotProduct < angleThreshold) {
            return false; // Не дивиться
        }

        // Перевіряємо чи немає перешкод
        RayTraceResult result = observer.getWorld().rayTrace(
                observerEye,
                observerDirection,
                GAZE_AVERSION_DISTANCE,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                entity -> entity.equals(target)
        );

        return result != null && result.getHitEntity() != null && result.getHitEntity().equals(target);
    }

    /**
     * Перевіряє чи може ціль опиратися примусовому відведенню погляду
     */
    private boolean canResistGazeAversion(IAbilityContext context, int arbiterSequence, UUID targetId) {
        Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);

        // Якщо не Beyonder - не може опиратися
        if (targetBeyonder == null) {
            return false;
        }

        int targetSequence = targetBeyonder.getSequenceLevel();

        // Розрахунок шансу опору
        SequenceBasedSuccessChance resistChance =
                new SequenceBasedSuccessChance(targetSequence, arbiterSequence);

        return resistChance.rollSuccess();
    }

    /**
     * Примусово відводить погляд гравця
     */
    private void forceLookAway(Player target, Player arbiter, IAbilityContext context) {
        Location targetLoc = target.getEyeLocation();
        Vector toArbiter = arbiter.getEyeLocation().toVector().subtract(targetLoc.toVector()).normalize();

        // Обчислюємо перпендикулярний вектор для відведення погляду
        Vector lookAwayDirection = toArbiter.getCrossProduct(new Vector(0, 1, 0)).normalize();

        // Якщо вектор нульовий (дивимося прямо вверх/вниз), використовуємо інший
        if (lookAwayDirection.lengthSquared() < 0.01) {
            lookAwayDirection = toArbiter.getCrossProduct(new Vector(1, 0, 0)).normalize();
        }

        // Розраховуємо новий напрямок погляду (відводимо на кут)
        Vector currentDirection = targetLoc.getDirection();
        double angle = Math.toRadians(LOOK_AWAY_ANGLE);

        // Обертаємо поточний напрямок погляду
        Vector newDirection = currentDirection.clone()
                .add(lookAwayDirection.multiply(Math.sin(angle)))
                .normalize();

        // Застосовуємо новий напрямок
        Location newLook = targetLoc.clone().setDirection(newDirection);
        target.teleport(newLook);

        // Візуальні ефекти
        target.getWorld().spawnParticle(
                Particle.ENCHANT,
                target.getEyeLocation(),
                10,
                0.2, 0.2, 0.2,
                0.05
        );

        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.3f, 1.5f);

        // Повідомлення цілі в action bar
        target.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.DARK_GRAY + "Ви не можете дивитися на " +
                        ChatColor.GOLD + arbiter.getName())
        );
    }

    /**
     * Обробка базової механіки погляду
     */
    private void processStare(IAbilityContext context, Player caster, Player target, StareState state) {
        if (target.getUniqueId().equals(state.targetId)) {
            state.ticksAccumulated++;

            // Візуальний ефект накопичення
            if (state.ticksAccumulated % 5 == 0) {
                target.spawnParticle(
                        Particle.ENCHANT,
                        target.getEyeLocation().add(0, 0.5, 0),
                        1, 0.1, 0.1, 0.1, 0.05
                );
            }

            // Перевірка порогу
            if (state.ticksAccumulated >= STARE_THRESHOLD_TICKS) {
                applyDebuff(context, caster, target);
                state.reset();
            }
        } else {
            // Зміна цілі
            state.targetId = target.getUniqueId();
            state.ticksAccumulated = 0;
        }
    }

    /**
     * Накладає дебаф Слабкість на ціль
     */
    private void applyDebuff(IAbilityContext context, Player caster, Player target) {
        // Отримуємо послідовності
        Beyonder casterBeyonder = context.getCasterBeyonder();
        Beyonder targetBeyonder = context.getBeyonderFromEntity(target.getUniqueId());

        int casterSequence = casterBeyonder.getSequenceLevel();

        // Якщо ціль не Beyonder - завжди накладаємо
        if (targetBeyonder == null) {
            applyWeaknessEffect(target, caster);
            return;
        }

        int targetSequence = targetBeyonder.getSequenceLevel();

        // Перевіряємо чи може ціль опиратися
        SequenceBasedSuccessChance successChance =
                new SequenceBasedSuccessChance(casterSequence, targetSequence);

        if (successChance.rollSuccess()) {
            // Успішно накладено слабкість
            applyWeaknessEffect(target, caster);
        } else {
            // Ціль опиралася
            target.getWorld().spawnParticle(
                    Particle.FIREWORK,
                    target.getEyeLocation(),
                    15,
                    0.3, 0.5, 0.3,
                    0.1
            );
            target.playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);

            // Повідомлення
            caster.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GRAY + target.getName() +
                            " опирається вашому погляду" +
                            ChatColor.DARK_GRAY + " (" + successChance.getFormattedChance() + ")")
            );

            target.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GREEN + "Ви опиралися Погляду Судді!")
            );
        }
    }
    private void applyWeaknessEffect(Player target, Player caster) {
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS,
                EFFECT_DURATION_TICKS,
                0
        ));

        // Звуки
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        caster.playSound(caster.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);

        // Частинки
        target.getWorld().spawnParticle(
                Particle.CRIT,
                target.getEyeLocation(),
                15, 0.3, 0.5, 0.3, 0.1
        );
        target.getWorld().spawnParticle(
                Particle.LARGE_SMOKE,
                target.getLocation(),
                5, 0.2, 1.0, 0.2, 0.05
        );

        // Action bar повідомлення для Арбітра
        caster.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GOLD + "✦ Придушили волю " +
                        ChatColor.RED + target.getName())
        );

        // Action bar для цілі
        target.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GRAY + "Ваші рухи слабшають під пильним поглядом...")
        );
    }

    @Override
    public void cleanUp() {
        stareStates.clear();
        gazeAccumulators.clear();
    }

    /**
     * Внутрішній клас для зберігання стану таймера погляду
     */
    private static class StareState {
        UUID targetId = null;
        int ticksAccumulated = 0;

        void reset() {
            targetId = null;
            ticksAccumulated = 0;
        }
    }

    /**
     * Внутрішній клас для акумуляції погляду інших гравців на Арбітра
     */
    private static class GazeAccumulator {
        int ticksAccumulated = 0;
        long cooldownUntil = 0;

        void reset(long currentTime) {
            ticksAccumulated = 0;
            cooldownUntil = currentTime + (GAZE_AVERSION_COOLDOWN * 50); // Конвертуємо тіки в мілісекунди
        }

        boolean isOnCooldown(long currentTime) {
            return currentTime < cooldownUntil;
        }
    }
}