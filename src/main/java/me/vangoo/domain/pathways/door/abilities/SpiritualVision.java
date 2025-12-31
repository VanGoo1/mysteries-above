package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class SpiritualVision extends ToggleablePassiveAbility {

    // Відстежування таймерів погляду для кожного гравця (кастер -> ціль -> тривалість)
    private final Map<UUID, Map<UUID, Integer>> gazeDuration = new HashMap<>();

    // Список гравців, на яких вже активовано ауру (кастер -> цілі)
    private final Map<UUID, Set<UUID>> activeAuras = new HashMap<>();

    private static final int REQUIRED_GAZE_TICKS = 20; // 1 секунда
    private static final double GAZE_MAX_DISTANCE = 30.0;
    private static final double GAZE_CONE_ANGLE = 30.0;

    @Override
    public String getName() {
        return "[Пасивна] Духовне бачення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дивлячись на гравця протягом 1 секунди, ви починаєте бачити його духовну ауру, " +
                "яка змінює колір залежно від здоров'я цілі.";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        context.sendMessageToCaster(ChatColor.DARK_PURPLE + "✦ Духовне бачення активовано");
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);

        UUID casterId = context.getCaster().getUniqueId();
        gazeDuration.put(casterId, new HashMap<>());
        activeAuras.put(casterId, new HashSet<>());
    }

    @Override
    public void onDisable(IAbilityContext context) {
        context.sendMessageToCaster(ChatColor.GRAY + "✦ Духовне бачення деактивовано");
        context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);

        UUID casterId = context.getCaster().getUniqueId();
        gazeDuration.remove(casterId);
        activeAuras.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID casterId = caster.getUniqueId();
        Location eyeLocation = caster.getEyeLocation();
        Vector lookDirection = eyeLocation.getDirection();

        // Отримуємо персональні дані цього кастера
        Map<UUID, Integer> casterGazeDuration = gazeDuration.get(casterId);
        Set<UUID> casterActiveAuras = activeAuras.get(casterId);

        // Якщо дані не ініціалізовані - створюємо
        if (casterGazeDuration == null) {
            casterGazeDuration = new HashMap<>();
            gazeDuration.put(casterId, casterGazeDuration);
        }
        if (casterActiveAuras == null) {
            casterActiveAuras = new HashSet<>();
            activeAuras.put(casterId, casterActiveAuras);
        }

        // Знаходимо всіх гравців у радіусі
        List<Player> nearbyPlayers = context.getNearbyPlayers(GAZE_MAX_DISTANCE);

        Set<UUID> currentlyGazedPlayers = new HashSet<>();

        for (Player target : nearbyPlayers) {
            if (target.getUniqueId().equals(casterId)) {
                continue; // Пропускаємо самого кастера
            }

            UUID targetId = target.getUniqueId();
            Location targetLocation = target.getEyeLocation();

            // Перевіряємо, чи дивиться кастер на цього гравця
            if (isLookingAt(eyeLocation, lookDirection, targetLocation)) {
                currentlyGazedPlayers.add(targetId);

                // Збільшуємо таймер погляду
                int currentDuration = casterGazeDuration.getOrDefault(targetId, 0);
                currentDuration++;
                casterGazeDuration.put(targetId, currentDuration);

                // Якщо подивився достатньо довго - активуємо ауру
                if (currentDuration >= REQUIRED_GAZE_TICKS && !casterActiveAuras.contains(targetId)) {
                    casterActiveAuras.add(targetId);

                    context.playSoundToCaster(Sound.BLOCK_BELL_USE, 0.3f, 2.0f);
                    context.sendMessageToCaster(ChatColor.LIGHT_PURPLE + "➤ Ви відчуваєте ауру " +
                            ChatColor.WHITE + target.getName());
                }

                // Оновлюємо ауру для активних
                if (casterActiveAuras.contains(targetId)) {
                    spawnAuraParticles(context, target);
                }
            }
        }

        // Перевіряємо активні аури - чи гравець все ще в радіусі та чи дивимося на нього
        Iterator<UUID> auraIterator = casterActiveAuras.iterator();
        while (auraIterator.hasNext()) {
            UUID targetId = auraIterator.next();

            // Якщо не дивимося на гравця або він далеко - видаляємо ауру
            if (!currentlyGazedPlayers.contains(targetId)) {
                auraIterator.remove();
                casterGazeDuration.remove(targetId);
            } else {
                // Перевіряємо чи гравець все ще в радіусі
                Player target = Bukkit.getPlayer(targetId);
                if (target == null || !target.isOnline() ||
                        target.getLocation().distance(caster.getLocation()) > GAZE_MAX_DISTANCE) {
                    auraIterator.remove();
                    casterGazeDuration.remove(targetId);
                }
            }
        }

        // Очищаємо таймери для тих, на кого не дивимося
        casterGazeDuration.keySet().removeIf(id -> !currentlyGazedPlayers.contains(id));
    }

    /**
     * Перевіряє, чи дивиться кастер на ціль
     */
    private boolean isLookingAt(Location eyeLocation, Vector lookDirection, Location targetLocation) {
        Vector toTarget = targetLocation.toVector().subtract(eyeLocation.toVector()).normalize();
        double angle = Math.toDegrees(Math.acos(lookDirection.dot(toTarget)));

        return angle <= GAZE_CONE_ANGLE;
    }

    /**
     * Спавнить частинки аури навколо гравця - як ефект зілля в Minecraft
     * Тільки кастер бачить ці партікли
     */
    private void spawnAuraParticles(IAbilityContext context, Player target) {
        Player caster = context.getCaster();
        Color color = getHealthColor(target);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.8f);

        // Спавнимо 1-3 партікли за тік (як у ванільних зіль)
        int particleCount = 1 + (int)(Math.random() * 3); // 1-3 партікли

        for (int i = 0; i < particleCount; i++) {
            // Випадковий кут навколо гравця (360 градусів)
            double angle = Math.random() * 360;
            double radians = Math.toRadians(angle);

            // Радіус навколо гравця (близько до тіла, як у зілля)
            double radius = 0.25 + Math.random() * 0.15; // 0.25-0.4 блоків

            // Позиція X та Z навколо гравця
            double x = Math.cos(radians) * radius;
            double z = Math.sin(radians) * radius;

            // Випадкова висота старту від ніг до голови (0 до 1.8)
            double startHeight = Math.random() * 1.8;

            // Початкова позиція партікла
            Location spawnLoc = target.getLocation().add(x, startHeight, z);

            // Партікли летять ВГОРУ (як у зілля)
            // Вертикальна швидкість руху вгору
            double upwardSpeed = 0.08 + Math.random() * 0.04; // 0.08-0.12

            // Мінімальний горизонтальний дрейф (майже вертикально вгору)
            double driftX = (Math.random() - 0.5) * 0.015;
            double driftZ = (Math.random() - 0.5) * 0.015;

            Vector velocity = new Vector(driftX, upwardSpeed, driftZ);

            // Спавнимо партікл ТІЛЬКИ ДЛЯ КАСТЕРА
            caster.spawnParticle(
                    Particle.DUST,
                    spawnLoc,
                    0, // count = 0, використовуємо власний вектор
                    velocity.getX(),
                    velocity.getY(),
                    velocity.getZ(),
                    1.0, // швидкість (для DUST це не має значення, але залишаємо)
                    dustOptions
            );
        }
    }

    /**
     * Визначає колір аури залежно від здоров'я
     */
    private Color getHealthColor(LivingEntity entity) {
        double health = entity.getHealth();
        double maxHealth = Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue();
        double healthPercentage = health / maxHealth;

        if (healthPercentage > 0.75) {
            // Зелений (повне здоров'я)
            return Color.fromRGB(0, 255, 100);
        } else if (healthPercentage > 0.5) {
            // Жовто-зелений
            return Color.fromRGB(150, 255, 0);
        } else if (healthPercentage > 0.25) {
            // Жовтий/помаранчевий
            return Color.fromRGB(255, 200, 0);
        } else {
            // Червоний (мало здоров'я)
            return Color.fromRGB(255, 50, 50);
        }
    }

    @Override
    public void cleanUp() {
        // Очищаємо всі дані при видаленні здібності
        gazeDuration.clear();
        activeAuras.clear();
    }
}