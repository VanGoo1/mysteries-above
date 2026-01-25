package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.context.IEntityContext;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import org.bukkit.Particle.DustOptions;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TravellersDoor extends ActiveAbility {

    private static final int COST = 300;
    private static final int COOLDOWN = 50;
    private static final int SPECTATOR_DURATION = 1200; // 60 seconds
    private static final int MAX_TELEPORT_DISTANCE = 5000;
    private static final int DOOR_TIMEOUT = 1200; // 60 seconds door lifetime

    // Зберігає дані про активні двері
    private static final Map<UUID, DoorData> activeDoors = new ConcurrentHashMap<>();
    // Зберігає всіх, хто зараз у режимі привидів (і кастерів, і пасажирів)
    private static final Set<UUID> activeSpectators = ConcurrentHashMap.newKeySet();
    // Зберігає пасажирів для конкретного кастера: CasterUUID -> Set<PassengerUUID>
    private static final Map<UUID, Set<UUID>> sessionPassengers = new ConcurrentHashMap<>();

    private static final Set<UUID> awaitingCoordinates = new HashSet<>();

    // Зберігаємо таймери для скасування
    private static final Map<UUID, BukkitTask> spectatorTimeouts = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Двері Мандрівника";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "ПКМ: створити двері в режим спостереження (exit в чат для виходу). " +
                "Shift+ПКМ: створити двері телепортації (макс. " + MAX_TELEPORT_DISTANCE + " блоків).";
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

        // Перевірка: Якщо гравець вже у спектаторі і використовує предмет - НЕ ВИХОДИМО
        if (activeSpectators.contains(context.getCasterId())) {
            // Просто ігноруємо використання предмету
            return AbilityResult.failure("Ви вже в режимі спостереження");
        }

        if (context.playerData().isSneaking(context.getCasterId())) {
            return handleTeleportMode(context);
        } else {
            return handleSpectatorMode(context);
        }
    }

    @Override
    protected void preExecution(IAbilityContext context) {
        // 1. Підписка на вихід гравців з серверу
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                PlayerQuitEvent.class,
                e -> activeSpectators.contains(e.getPlayer().getUniqueId()),
                e -> handlePlayerQuit(context.entity(), e.getPlayer().getUniqueId()),
                Integer.MAX_VALUE
        );

        // 2. Підписка на exit для виходу з режиму спостереження
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                AsyncPlayerChatEvent.class,
                e -> activeSpectators.contains(e.getPlayer().getUniqueId()) &&
                        e.getMessage().equalsIgnoreCase("exit"),
                e -> {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("Mysteries-Above"),
                            () -> exitSpectatorMode(context, e.getPlayer().getUniqueId())
                    );
                },
                Integer.MAX_VALUE
        );
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        // Кулдаун встановлюється автоматично тільки якщо гравець НЕ у спектаторі
    }

    private void handlePlayerQuit(IEntityContext entityContext, UUID playerId) {
        if (activeSpectators.contains(playerId)) {
            activeSpectators.remove(playerId);

            // Для показу гравців назад потрібен повний контекст, але тут його немає
            // Тому просто міняємо GameMode - при наступному логіні гравці будуть видимі
            entityContext.setGameMode(playerId, GameMode.SURVIVAL);

            // Очистка сесії, якщо вийшов кастер
            sessionPassengers.remove(playerId);
        }
    }

    private AbilityResult handleSpectatorMode(IAbilityContext context) {
        Location doorLoc = context.getCasterEyeLocation().add(context.getCasterLocation().getDirection().multiply(2));
        Vector direction = context.getCasterLocation().getDirection();

        DoorData door = new DoorData(doorLoc, DoorType.SPECTATOR, context.getCasterId(), null);
        activeDoors.put(context.getCasterId(), door);

        createPersistentDoor(context, door, direction);

        // ВИПРАВЛЕННЯ: Зберігаємо UUID підписки для можливості відписатися
        UUID monitoringSubscriptionId = UUID.randomUUID();
        startDoorMonitoring(context, door, monitoringSubscriptionId);

        // Автоматичне видалення двері через таймаут
        context.scheduling().scheduleDelayed(() -> {
            if (!door.casterEntered && activeDoors.containsValue(door)) {
                activeDoors.remove(door.casterId);

                // ВИПРАВЛЕННЯ: Відписуємось від події руху, щоб портал більше не працював
                context.events().unsubscribeAll(monitoringSubscriptionId);

                context.messaging().sendMessageToActionBar(door.casterId,
                        Component.text("Двері зникли через неактивність").color(NamedTextColor.YELLOW));
            }
        }, DOOR_TIMEOUT);

        context.effects().playSound(doorLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
        context.messaging().sendMessageToActionBar(context.getCasterId(),
                Component.text("Двері створено.").color(NamedTextColor.AQUA));

        return AbilityResult.success();
    }

    private AbilityResult handleTeleportMode(IAbilityContext context) {
        awaitingCoordinates.add(context.getCasterId());
        String message = ChatColor.GOLD + """
                ╔══════════════════════════════════╗
                """ +
                ChatColor.YELLOW + " Введіть координати телепортації:\n" +
                ChatColor.GRAY + " Формат: X Y Z\n" +
                ChatColor.GRAY + " Приклад: 100 64 -200\n" +
                ChatColor.DARK_GRAY + " Макс. відстань: " + MAX_TELEPORT_DISTANCE + " блоків\n" +
                ChatColor.GOLD + "╚══════════════════════════════════╝";
        context.messaging().sendMessage(context.getCasterId(), message);
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                AsyncPlayerChatEvent.class,
                e -> e.getPlayer().getUniqueId().equals(context.getCasterId()) &&
                        awaitingCoordinates.contains(context.getCasterId()),
                e -> {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("Mysteries-Above"),
                            () -> handleCoordinatesInput(context, context.getCasterId(), e.getMessage())
                    );
                },
                600
        );

        return AbilityResult.deferred();
    }

    private void handleCoordinatesInput(IAbilityContext context, UUID casterId, String input) {
        if (!awaitingCoordinates.contains(casterId)) {
            return;
        }

        awaitingCoordinates.remove(casterId);

        try {
            String[] parts = input.trim().split("\\s+");
            if (parts.length != 3) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Невірний формат! Використовуйте: X Y Z");
                return;
            }

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            Location currentLoc = context.getCasterLocation();
            Location targetLoc = new Location(currentLoc.getWorld(), x, y, z);

            double distance = currentLoc.distance(targetLoc);
            if (distance > MAX_TELEPORT_DISTANCE) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Занадто далеко! Відстань: " +
                        String.format("%.0f", distance) + " > " + MAX_TELEPORT_DISTANCE);
                return;
            }

            createTeleportDoor(context, casterId, targetLoc);

        } catch (NumberFormatException e) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Невірні координати! Використовуйте числа.");
        }
    }

    private void createTeleportDoor(IAbilityContext context, UUID casterId, Location target) {
        Beyonder casterBeyonder = context.getCasterBeyonder();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності для створення двері!");
            return;
        }

        Location doorLoc = context.getCasterEyeLocation().add(context.getCasterLocation().getDirection().multiply(2));
        Vector direction = context.getCasterLocation().getDirection();

        DoorData door = new DoorData(doorLoc, DoorType.TELEPORT, casterId, target);
        activeDoors.put(casterId, door);

        createPersistentDoor(context, door, direction);

        // ВИПРАВЛЕННЯ: Зберігаємо UUID підписки
        UUID monitoringSubscriptionId = UUID.randomUUID();
        startDoorMonitoring(context, door, monitoringSubscriptionId);

        context.scheduling().scheduleDelayed(() -> {
            if (!door.casterEntered && activeDoors.containsValue(door)) {
                activeDoors.remove(door.casterId);

                // ВИПРАВЛЕННЯ: Відписуємось від події
                context.events().unsubscribeAll(monitoringSubscriptionId);

                context.messaging().sendMessage(casterId, ChatColor.YELLOW + "Двері зникли через неактивність");
            }
        }, DOOR_TIMEOUT);

        context.effects().playSound(doorLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("Двері створено.").color(NamedTextColor.AQUA));
    }

    // ВИПРАВЛЕННЯ: Додано параметр subscriptionId
    private void startDoorMonitoring(IAbilityContext context, DoorData door, UUID subscriptionId) {
        context.events().subscribeToTemporaryEvent(subscriptionId,
                PlayerMoveEvent.class,
                e -> {
                    // Перевіряємо, чи двері ще активні
                    if (!activeDoors.containsValue(door)) {
                        return false; // Двері вже видалені
                    }

                    // Перевіряємо, чи гравець не заходив раніше
                    if (door.enteredPlayers.contains(e.getPlayer().getUniqueId())) {
                        return false; // Гравець вже входив
                    }

                    // ВИПРАВЛЕННЯ: Перевіряємо, чи гравець у тому ж світі, що й двері
                    if (e.getTo() == null || !e.getTo().getWorld().equals(door.location.getWorld())) {
                        return false; // Різні світи - не можна порівнювати відстань
                    }

                    // Перевіряємо відстань
                    return e.getTo().distance(door.location) < 1.5;
                },
                e -> handlePlayerEnterDoor(context, door, e.getPlayer().getUniqueId(), subscriptionId),
                DOOR_TIMEOUT + 100
        );
    }
    // ВИПРАВЛЕННЯ: Додано параметр subscriptionId
    private void handlePlayerEnterDoor(IAbilityContext context, DoorData door, UUID playerId, UUID subscriptionId) {
        // ВИПРАВЛЕННЯ: Подвійна перевірка, чи гравець вже увійшов
        if (door.enteredPlayers.contains(playerId)) {
            return;
        }

        // Додаємо гравця в список тих, хто увійшов
        door.enteredPlayers.add(playerId);

        boolean isCaster = playerId.equals(door.casterId);

        if (door.type == DoorType.SPECTATOR) {
            handleSpectatorEntry(context, door, playerId, isCaster);
        } else {
            handleTeleportEntry(context, door, playerId, isCaster);
        }

        if (isCaster) {
            door.casterEntered = true;
            activeDoors.remove(door.casterId);

            // ВИПРАВЛЕННЯ: Відписуємось від моніторингу після входу кастера
            context.events().unsubscribeAll(subscriptionId);
        }
    }

    private void handleSpectatorEntry(IAbilityContext context, DoorData door, UUID playerId, boolean isCaster) {
        context.entity().setGameMode(playerId, GameMode.SPECTATOR);
        activeSpectators.add(playerId);

        // КРИТИЧНО: Заборонити телепортацію через меню спектатора
        List<Player> onlinePlayers = context.targeting().getNearbyPlayers(10000);
        for (Player onlinePlayer : onlinePlayers) {
            if (!onlinePlayer.getUniqueId().equals(playerId)) {
                context.entity().hidePlayerFromTarget(playerId, onlinePlayer.getUniqueId());
            }
        }

        context.effects().playSound(context.playerData().getCurrentLocation(playerId), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
        context.effects().spawnParticle(Particle.REVERSE_PORTAL, context.playerData().getCurrentLocation(playerId), 30, 0.5, 1, 0.5);

        if (!isCaster) {
            context.messaging().sendMessageToActionBar(playerId,
                    Component.text(ChatColor.AQUA + "Ви увійшли у простір. Чекайте на провідника."));

            sessionPassengers.computeIfAbsent(door.casterId, k -> ConcurrentHashMap.newKeySet())
                    .add(playerId);

            if (context.playerData().isOnline(door.casterId)) {
                context.messaging().sendMessageToActionBar(door.casterId,
                        Component.text(context.playerData().getName(playerId) + " увійшов у ваші двері.")
                                .color(NamedTextColor.GREEN));
            }

        } else {
            context.messaging().sendMessageToActionBar(context.getCasterId(),
                    Component.text("Подорожування активовано на 60 секунд").color(NamedTextColor.AQUA));
            context.messaging().sendMessageToActionBar(context.getCasterId(),
                    Component.text("Напишіть в чат exit, щоб завершити.").color(NamedTextColor.GREEN));

            startTetheringTask(context, playerId);

            // ВИПРАВЛЕННЯ: Зберігаємо таймер і скасовуємо його при ручному виході
            BukkitTask timeoutTask = context.scheduling().scheduleDelayed(() -> {
                // Перевіряємо чи гравець ще у спектаторі
                if (activeSpectators.contains(playerId)) {
                    exitSpectatorMode(context, playerId);
                    context.messaging().sendMessageToActionBar(playerId,
                            Component.text("Час спостереження вийшов.").color(NamedTextColor.YELLOW));
                }
            }, SPECTATOR_DURATION);

            // Зберігаємо таймер для можливості скасування
            spectatorTimeouts.put(playerId, timeoutTask);
        }
    }
    private void startTetheringTask(IAbilityContext context, UUID casterId) {
        context.scheduling().scheduleRepeating(() -> {
            if (!activeSpectators.contains(casterId)) {
                return;
            }

            Set<UUID> passengers = sessionPassengers.get(casterId);
            if (passengers != null && !passengers.isEmpty()) {
                Location casterLoc = context.playerData().getCurrentLocation(casterId);
                if (casterLoc == null) return;

                for (UUID passengerId : passengers) {
                    Player p = Bukkit.getPlayer(passengerId);
                    if (p != null && p.isOnline() && activeSpectators.contains(passengerId)) {
                        if (p.getWorld().equals(casterLoc.getWorld())) {
                            if (p.getLocation().distanceSquared(casterLoc) > 1) {
                                p.teleport(casterLoc);
                            }
                        } else {
                            p.teleport(casterLoc);
                        }
                    }
                }
            }
        }, 0L, 1L);
    }

    private void handleTeleportEntry(IAbilityContext context, DoorData door, UUID playerId, boolean isCaster) {
        context.entity().teleport(playerId, door.targetLocation);

        Location exitDoor = door.targetLocation.clone().add(0, 0.5, 0);
        createMysticalDoor(context, exitDoor, new Vector(1, 0, 0));

        context.effects().playSound(door.targetLocation, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
        context.effects().spawnParticle(Particle.REVERSE_PORTAL, door.targetLocation, 50, 1, 1, 1);

        context.messaging().sendMessageToActionBar(playerId,
                Component.text("Телепортація виконана!").color(NamedTextColor.GREEN));
    }

    private void exitSpectatorMode(IAbilityContext context, UUID playerId) {
        if (!activeSpectators.contains(playerId)) return;

        // ВИПРАВЛЕННЯ: Скасовуємо таймер автовиходу
        BukkitTask timeoutTask = spectatorTimeouts.remove(playerId);
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }

        activeSpectators.remove(playerId);

        // Решта коду залишається без змін...
        if (context.playerData().isOnline(playerId)) {
            List<Player> onlinePlayers = context.targeting().getNearbyPlayers(10000);
            for (Player onlinePlayer : onlinePlayers) {
                context.entity().showPlayerToTarget(playerId, onlinePlayer.getUniqueId());
            }

            context.entity().setGameMode(playerId, GameMode.SURVIVAL);
        } else {
            context.entity().setGameMode(playerId, GameMode.SURVIVAL);
        }

        if (context.playerData().getEyeLocation(playerId) != null &&
                context.playerData().getEyeLocation(playerId).getBlock().getType().isSolid()) {
            context.entity().teleport(playerId, context.playerData().getCurrentLocation(playerId).add(0, 1, 0));
        }

        sessionPassengers.values().forEach(set -> set.remove(playerId));

        if (sessionPassengers.containsKey(playerId)) {
            Set<UUID> passengers = sessionPassengers.remove(playerId);
            if (passengers != null) {
                for (UUID passengerId : passengers) {
                    if (context.playerData().isOnline(passengerId) && activeSpectators.contains(passengerId)) {
                        activeSpectators.remove(passengerId);

                        if (context.playerData().isOnline(passengerId)) {
                            List<Player> onlinePlayers = context.targeting().getNearbyPlayers(10000);
                            for (Player onlinePlayer : onlinePlayers) {
                                context.entity().showPlayerToTarget(passengerId, onlinePlayer.getUniqueId());
                            }
                        }

                        context.entity().setGameMode(passengerId, GameMode.SURVIVAL);
                        context.entity().teleport(passengerId, context.playerData().getCurrentLocation(playerId));

                        context.messaging().sendMessageToActionBar(passengerId,
                                Component.text("Провідник покинув простір.").color(NamedTextColor.YELLOW));
                    }
                }
            }
        }

        context.cooldown().setCooldown(this, playerId);

        Location exitLoc = context.playerData().getCurrentLocation(playerId);
        createMysticalDoor(context, exitLoc, new Vector(0, 0, 1));

        context.effects().playSound(exitLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);
        context.effects().spawnParticle(Particle.REVERSE_PORTAL, exitLoc, 30, 0.5, 1, 0.5);
    }

    private void createPersistentDoor(IAbilityContext context, DoorData door, Vector direction) {
        Vector dir = direction.clone().setY(0).normalize();
        if (dir.lengthSquared() == 0) dir = new Vector(0, 0, 1);

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        double width = 1.2;
        double height = 2.2;

        DustOptions portalDust = new DustOptions(Color.fromRGB(100, 200, 255), 1.2f);
        DustOptions glowDust = new DustOptions(Color.fromRGB(200, 230, 255), 0.8f);

        for (int frame = 0; frame < 20; frame++) {
            int finalFrame = frame;
            Vector finalDir = dir;
            context.scheduling().scheduleDelayed(() -> {
                double progress = finalFrame / 20.0;

                drawDoorFrame(context, door.location, right, width * progress, height * progress, portalDust);

                if (progress > 0.5) {
                    fillDoorPortal(context, door.location, right, finalDir,
                            width * progress, height * progress, glowDust);
                }

                if (finalFrame % 5 == 0) {
                    context.effects().playSound(door.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.5f, 1.5f + finalFrame * 0.05f);
                }
            }, frame);
        }

        maintainDoorVisuals(context, door, right, dir, width, height, portalDust, glowDust);
    }

    private void maintainDoorVisuals(IAbilityContext context, DoorData door, Vector right, Vector dir,
                                     double width, double height, DustOptions portalDust, DustOptions glowDust) {
        context.scheduling().scheduleRepeating(() -> {
            if (door.casterEntered || !activeDoors.containsValue(door)) {
                return;
            }

            drawDoorFrame(context, door.location, right, width, height, portalDust);
            fillDoorPortal(context, door.location, right, dir, width, height, glowDust);
        }, 20L, 10L);
    }

    private void createMysticalDoor(IAbilityContext context, Location center, Vector direction) {
        Vector dir = direction.clone().setY(0).normalize();
        if (dir.lengthSquared() == 0) dir = new Vector(0, 0, 1);

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        double width = 1.2;
        double height = 2.2;

        DustOptions portalDust = new DustOptions(Color.fromRGB(100, 200, 255), 1.2f);
        DustOptions glowDust = new DustOptions(Color.fromRGB(200, 230, 255), 0.8f);

        for (int frame = 0; frame < 20; frame++) {
            int finalFrame = frame;
            Vector finalDir = dir;
            context.scheduling().scheduleDelayed(() -> {
                double progress = finalFrame / 20.0;
                drawDoorFrame(context, center, right, width * progress, height * progress, portalDust);

                if (progress > 0.5) {
                    fillDoorPortal(context, center, right, finalDir, width * progress, height * progress, glowDust);
                }
            }, frame);
        }
    }

    private void drawDoorFrame(IAbilityContext context, Location center, Vector right,
                               double width, double height, DustOptions dust) {
        World world = center.getWorld();

        for (double y = 0; y <= height; y += 0.15) {
            Location left = center.clone().add(right.clone().multiply(-width / 2)).add(0, y - height / 2, 0);
            Location rightPos = center.clone().add(right.clone().multiply(width / 2)).add(0, y - height / 2, 0);

            world.spawnParticle(Particle.DUST, left, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, rightPos, 1, 0, 0, 0, 0, dust);
        }

        for (double x = -width / 2; x <= width / 2; x += 0.15) {
            Location top = center.clone().add(right.clone().multiply(x)).add(0, height / 2, 0);
            Location bottom = center.clone().add(right.clone().multiply(x)).add(0, -height / 2, 0);

            world.spawnParticle(Particle.DUST, top, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, bottom, 1, 0, 0, 0, 0, dust);
        }
    }

    private void fillDoorPortal(IAbilityContext context, Location center, Vector right, Vector forward,
                                double width, double height, DustOptions dust) {
        World world = center.getWorld();
        Random rand = new Random();

        for (int i = 0; i < 15; i++) {
            double x = (rand.nextDouble() - 0.5) * width * 0.8;
            double y = (rand.nextDouble() - 0.5) * height * 0.8;
            double z = (rand.nextDouble() - 0.5) * 0.1;

            Location particle = center.clone()
                    .add(right.clone().multiply(x))
                    .add(0, y, 0)
                    .add(forward.clone().multiply(z));

            world.spawnParticle(Particle.DUST, particle, 1, 0, 0, 0, 0, dust);
        }
    }

    @Override
    public void cleanUp() {
        // Скасовуємо всі таймери
        for (BukkitTask task : spectatorTimeouts.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        spectatorTimeouts.clear();

        for (UUID playerId : activeSpectators) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }

        activeDoors.clear();
        activeSpectators.clear();
        sessionPassengers.clear();
        awaitingCoordinates.clear();
    }

    private enum DoorType {
        SPECTATOR,
        TELEPORT
    }

    private static class DoorData {
        final Location location;
        final DoorType type;
        final UUID casterId;
        final Location targetLocation;
        boolean casterEntered = false;
        final Set<UUID> enteredPlayers = new HashSet<>();

        DoorData(Location location, DoorType type, UUID casterId, Location targetLocation) {
            this.location = location;
            this.type = type;
            this.casterId = casterId;
            this.targetLocation = targetLocation;
        }
    }
}