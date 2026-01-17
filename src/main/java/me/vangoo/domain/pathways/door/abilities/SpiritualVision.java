package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class SpiritualVision extends ToggleablePassiveAbility {

    // Відстежування таймерів погляду для кожного гравця (кастер -> ціль -> тривалість)
    private final Map<UUID, Map<UUID, Integer>> gazeDuration = new HashMap<>();

    // Список гравців, на яких вже активовано ауру здоров'я (кастер -> цілі)
    private final Map<UUID, Set<UUID>> activeAuras = new HashMap<>();

    // Список гравців, чий шлях вже було розкрито в цій сесії погляду (кастер -> цілі)
    private final Map<UUID, Set<UUID>> revealedPathways = new HashMap<>();

    private static final int REQUIRED_GAZE_TICKS = 20; // 1 секунда для аури
    private static final int REVEAL_PATHWAY_TICKS = 100; // 5 секунд для шляху (20 тіків * 5 сек)
    private static final double GAZE_MAX_DISTANCE = 30.0;
    private static final double GAZE_CONE_ANGLE = 30.0;

    @Override
    public String getName() {
        return "[Пасивна] Духовне бачення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дивлячись на гравця 1 с, ви бачите ауру здоров'я. " +
                "Дивлячись 5 с — дізнаєтесь його Шлях.";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterPlayer().getUniqueId();
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);

        gazeDuration.put(casterId, new HashMap<>());
        activeAuras.put(casterId, new HashSet<>());
        revealedPathways.put(casterId, new HashSet<>());
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterPlayer().getUniqueId();
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);

        gazeDuration.remove(casterId);
        activeAuras.remove(casterId);
        revealedPathways.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        Location eyeLocation = context.playerData().getEyeLocation(casterId);
        Vector lookDirection = eyeLocation.getDirection();

        // Отримуємо або ініціалізуємо дані сесії
        Map<UUID, Integer> casterGazeDuration = gazeDuration.computeIfAbsent(casterId, k -> new HashMap<>());
        Set<UUID> casterActiveAuras = activeAuras.computeIfAbsent(casterId, k -> new HashSet<>());
        Set<UUID> casterRevealedPathways = revealedPathways.computeIfAbsent(casterId, k -> new HashSet<>());

        // Знаходимо всіх гравців у радіусі
        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(GAZE_MAX_DISTANCE);
        Set<UUID> currentlyGazedPlayers = new HashSet<>();

        for (Player target : nearbyPlayers) {
            if (target.getUniqueId().equals(casterId)) continue;

            UUID targetId = target.getUniqueId();
            Location targetLocation = target.getEyeLocation();

            // Перевіряємо, чи дивиться кастер на цього гравця
            if (isLookingAt(eyeLocation, lookDirection, targetLocation)) {
                currentlyGazedPlayers.add(targetId);

                // Збільшуємо таймер погляду
                int currentDuration = casterGazeDuration.getOrDefault(targetId, 0) + 1;
                casterGazeDuration.put(targetId, currentDuration);

                // --- ЕТАП 1: 1 секунда (20 тіків) - Аура здоров'я ---
                if (currentDuration >= REQUIRED_GAZE_TICKS) {
                    if (!casterActiveAuras.contains(targetId)) {
                        casterActiveAuras.add(targetId);
                        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 2.0f);
                        // Невелике повідомлення в actionbar
                        context.messaging().sendMessageToActionBar(casterId,
                                Component.text(ChatColor.GRAY + "Аура: " + ChatColor.WHITE + target.getName()));
                    }

                    // Спавнимо ауру
                    spawnAuraParticles(context, target);
                }

                // --- ЕТАП 2: 5 секунд (100 тіків) - Розкриття Шляху ---
                if (currentDuration >= REVEAL_PATHWAY_TICKS && !casterRevealedPathways.contains(targetId)) {
                    revealPathwayInfo(context, casterId, target);
                    casterRevealedPathways.add(targetId); // Помічаємо, що вже сказали
                }
            }
        }

        // Очищення: якщо ми перестали дивитися або гравець вийшов
        // Iterating over a copy to avoid ConcurrentModificationException or complicated logic
        Set<UUID> targetsCleanup = new HashSet<>(casterGazeDuration.keySet());

        for (UUID targetId : targetsCleanup) {
            boolean shouldReset = false;

            // 1. Якщо ми зараз не дивимося на нього
            if (!currentlyGazedPlayers.contains(targetId)) {
                shouldReset = true;
            }
            // 2. Якщо гравець зник/офлайн/далеко
            else {
                // Використовуємо методи контексту: isOnline та getCurrentLocation
                if (!context.playerData().isOnline(targetId) ||
                        context.playerData().getCurrentLocation(targetId).distance(context.playerData().getCurrentLocation(casterId)) > GAZE_MAX_DISTANCE) {
                    shouldReset = true;
                }
            }

            if (shouldReset) {
                casterGazeDuration.remove(targetId);
                casterActiveAuras.remove(targetId);
                casterRevealedPathways.remove(targetId); // Скидаємо статус розкритого шляху
            }
        }
    }

    /**
     * Логіка розкриття шляху
     */
    private void revealPathwayInfo(IAbilityContext context, UUID casterId, Player target) {
        Beyonder targetBeyonder = context.beyonder().getBeyonder(target.getUniqueId());

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);

        if (targetBeyonder != null) {
            String pathwayName = targetBeyonder.getPathway().getName();

            // Відправляємо повідомлення в чат (так надійніше для важливої інфо)
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.DARK_PURPLE + "✦ Духовний зір прояснюється..."));
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Сутність " + ChatColor.WHITE + target.getName() +
                    ChatColor.GRAY + " належить до шляху: " + ChatColor.AQUA + pathwayName));
        } else {
            // Якщо це звичайна людина
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.DARK_PURPLE + "✦ Духовний зір прояснюється..."));
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "У " + ChatColor.WHITE + target.getName() +
                    ChatColor.GRAY + " немає ознак потойбічності."));
        }
    }

    private boolean isLookingAt(Location eyeLocation, Vector lookDirection, Location targetLocation) {
        Vector toTarget = targetLocation.toVector().subtract(eyeLocation.toVector()).normalize();
        double angle = Math.toDegrees(Math.acos(lookDirection.dot(toTarget)));
        return angle <= GAZE_CONE_ANGLE;
    }

    private void spawnAuraParticles(IAbilityContext context, Player target) {
        Player caster = context.getCasterPlayer();
        Color color = getHealthColor(target);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.8f);

        int particleCount = 1 + (int)(Math.random() * 3);

        for (int i = 0; i < particleCount; i++) {
            double angle = Math.random() * 360;
            double radians = Math.toRadians(angle);
            double radius = 0.25 + Math.random() * 0.15;

            double x = Math.cos(radians) * radius;
            double z = Math.sin(radians) * radius;
            double startHeight = Math.random() * 1.8;

            Location spawnLoc = target.getLocation().add(x, startHeight, z);

            double upwardSpeed = 0.08 + Math.random() * 0.04;
            double driftX = (Math.random() - 0.5) * 0.015;
            double driftZ = (Math.random() - 0.5) * 0.015;

            Vector velocity = new Vector(driftX, upwardSpeed, driftZ);

            caster.spawnParticle(
                    Particle.DUST,
                    spawnLoc,
                    0,
                    velocity.getX(),
                    velocity.getY(),
                    velocity.getZ(),
                    1.0,
                    dustOptions
            );
        }
    }

    private Color getHealthColor(LivingEntity entity) {
        double health = entity.getHealth();
        double maxHealth = Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue();
        double healthPercentage = health / maxHealth;

        if (healthPercentage > 0.75) return Color.fromRGB(0, 255, 100);
        else if (healthPercentage > 0.5) return Color.fromRGB(150, 255, 0);
        else if (healthPercentage > 0.25) return Color.fromRGB(255, 200, 0);
        else return Color.fromRGB(255, 50, 50);
    }

    @Override
    public void cleanUp() {
        gazeDuration.clear();
        activeAuras.clear();
        revealedPathways.clear();
    }
}