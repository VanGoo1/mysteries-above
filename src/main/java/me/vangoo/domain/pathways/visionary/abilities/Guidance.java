package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Guidance extends ActiveAbility {

    private static final int MAX_DISTANCE = 10; // Радіус "повідця"
    private static final int DURATION_SECONDS = 300; // 5 хвилин
    private static final int CAST_RANGE = 10; // Дистанція до сплячого для активації
    private static final int COST = 400; // Вартість духовності
    private static final int COOLDOWN = 600; // Кулдаун 10 хвилин

    // Track active guidance sessions (caster UUID -> hasEnded flag)
    private static final Map<UUID, boolean[]> activeGuidanceSessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Керівництво";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви проникаєте у підсвідомість сплячого, встановлюючи ментальний зв'язок. " +
                "Протягом 5 хвилин ціль не зможе відійти від вас далі ніж на 10 блоків.\n" +
                ChatColor.GRAY + "Використайте здібність знову, щоб достроково розірвати зв'язок.";
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
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(CAST_RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Check if caster already has an active Guidance session
        if (activeGuidanceSessions.containsKey(casterId)) {
            // Manual cancel
            boolean[] hasEnded = activeGuidanceSessions.get(casterId);
            hasEnded[0] = true;
            activeGuidanceSessions.remove(casterId);

            context.sendMessageToActionBar(context.getCasterPlayer(),
                    Component.text("Ви розірвали ментальний зв'язок").color(NamedTextColor.YELLOW));
            context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f);
            return AbilityResult.success();
        }

        Optional<LivingEntity> targetOpt = context.getTargetedEntity(CAST_RANGE);

        if (targetOpt.isEmpty() || !(targetOpt.get() instanceof Player target)) {
            return AbilityResult.failure("Ціль має бути гравцем.");
        }

        // Перевірка: чи спить ціль?
        if (!target.isSleeping()) {
            return AbilityResult.failure("Ціль повинна спати, щоб встановити зв'язок.");
        }

        UUID targetId = target.getUniqueId();
        // Візуальні ефекти успіху
        context.sendMessageToActionBar(context.getCasterPlayer(),
                Component.text("Ви встановили ментальний контроль над " + target.getName())
                        .color(NamedTextColor.AQUA));
        context.sendMessageToActionBar(target,
                Component.text("Ви відчуваєте дивний сон, ніби хтось веде вас за руку...")
                        .color(NamedTextColor.LIGHT_PURPLE));

        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
        context.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5);

        // Блокуємо посадку в транспорт
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                VehicleEnterEvent.class,
                e -> e.getEntered().getUniqueId().equals(targetId),
                e -> {
                    e.setCancelled(true);
                    context.sendMessageToActionBar(target,
                            Component.text("Ментальний контроль не дозволяє вам тут сховатись!")
                                    .color(NamedTextColor.RED));
                },
                DURATION_SECONDS * 20
        );

        // Головна логіка моніторингу відстані
        startDistanceMonitoring(context, casterId, targetId);
        return AbilityResult.success();
    }

    /**
     * Моніторинг відстані через context.scheduleRepeating
     */
    private void startDistanceMonitoring(IAbilityContext context, UUID casterId, UUID targetId) {
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + (DURATION_SECONDS * 1000L);
        final long[] lastMessageTime = {0}; // Трекер для уникнення спаму
        final boolean[] hasEnded = {false}; // Flag to prevent spam

        // Register this session
        activeGuidanceSessions.put(casterId, hasEnded);

        context.scheduleRepeating(new Runnable() {
            @Override
            public void run() {
                // Check if already ended - prevent spam
                if (hasEnded[0]) {
                    activeGuidanceSessions.remove(casterId);
                    return;
                }

                long now = System.currentTimeMillis();

                // Перевірка закінчення часу
                if (now >= endTime) {
                    hasEnded[0] = true;
                    activeGuidanceSessions.remove(casterId);
                    // Повідомлення про завершення
                    context.sendMessage(targetId, ChatColor.GREEN + "Ви відчуваєте полегшення. Ментальний поводок зник.");
                    context.sendMessage(casterId, ChatColor.GREEN + "Дія 'Керівництва' завершилась.");
                    return;
                }

                Player target = getPlayerSafely(context, targetId);
                Player caster = getPlayerSafely(context, casterId);

                // Валідація: чи онлайн обидва гравці
                if (target == null || caster == null) {
                    return; // Продовжуємо чекати (можливо тимчасово офлайн)
                }

                // Перевірка світів
                if (!target.getWorld().equals(caster.getWorld())) {
                    hasEnded[0] = true;
                    activeGuidanceSessions.remove(casterId);
                    context.damage(targetId, 2.0);
                    context.sendMessage(targetId, ChatColor.RED + "Зв'язок розірвано через зміну виміру!");
                    context.sendMessage(casterId, ChatColor.YELLOW + "Зв'язок з ціллю розірвано (інший світ)");
                    return;
                }

                // ГОЛОВНА ЛОГІКА: Перевірка відстані
                Location targetLoc = target.getLocation();
                Location casterLoc = caster.getLocation();
                double distance = targetLoc.distance(casterLoc);

                if (distance > MAX_DISTANCE) {
                    applyRestraint(context, targetId, casterId, targetLoc, casterLoc, now, lastMessageTime);
                }
            }
        }, 0L, 10L); // Кожні 0.5 секунди
    }

    /**
     * Безпечне отримання гравця (повертає null якщо офлайн)
     */
    private Player getPlayerSafely(IAbilityContext context, UUID playerId) {
        // Використовуємо context для отримання caster
        if (playerId.equals(context.getCasterId())) {
            return context.getCasterPlayer();
        }

        // Для інших гравців використовуємо Bukkit (це OK в цьому місці)
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        return (player != null && player.isOnline()) ? player : null;
    }

    /**
     * Застосування "повідця" - відштовхування назад до кастера
     */
    private void applyRestraint(IAbilityContext context, UUID targetId, UUID casterId,
                                Location targetLoc, Location casterLoc,
                                long now, long[] lastMessageTime) {

        // Вектор від цілі до кастера
        Vector direction = casterLoc.toVector().subtract(targetLoc.toVector());
        direction.normalize();

        // Динамічна сила залежно від відстані
        double distance = targetLoc.distance(casterLoc);
        double strength = 0.5 + ((distance - MAX_DISTANCE) * 0.05);
        strength = Math.min(strength, 2.5); // Ліміт

        direction.multiply(strength).setY(0.3); // Трохи підкидаємо

        // Застосовуємо velocity
        Player target = getPlayerSafely(context, targetId);
        if (target != null) {
            target.setVelocity(direction);
        }

        // Ефекти
        context.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        context.spawnParticle(Particle.REVERSE_PORTAL, targetLoc, 15, 0.5, 1, 0.5);

        // Повідомлення (без спаму - раз на 3 секунди)
        if (now - lastMessageTime[0] > 3000) {
            context.sendMessageToActionBar(target,
                    Component.text("Незрима сила тягне вас назад!").color(NamedTextColor.DARK_PURPLE));
            lastMessageTime[0] = now;
        }
    }

    @Override
    public void cleanUp() {
        // Нічого не треба чистити - scheduler tasks автоматично зупиняються
    }
}