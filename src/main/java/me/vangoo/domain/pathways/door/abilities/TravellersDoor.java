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
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import org.bukkit.Particle.DustOptions;

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

        // Перевірка: Якщо гравець вже у спектаторі і використовує предмет - теж викидаємо
        if (activeSpectators.contains(context.getCasterId())) {
            exitSpectatorMode(context, context.getCasterId());
            return AbilityResult.successWithMessage(ChatColor.YELLOW + "Ви завершили подорожування");
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
        // Якщо у спектаторі - кулдаун буде встановлено вручну при виході
    }

    private void handlePlayerQuit(IEntityContext entityContext, UUID playerId) {
        if (activeSpectators.contains(playerId)) {
            activeSpectators.remove(playerId);
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
        startDoorMonitoring(context, door);

        // Автоматичне видалення двері через таймаут
        context.scheduling().scheduleDelayed(() -> {
            if (!door.casterEntered && activeDoors.containsValue(door)) {
                activeDoors.remove(door.casterId);
                context.messaging().sendMessageToActionBar(door.casterId, Component.text("Двері зникли через неактивність").color(NamedTextColor.YELLOW));
            }
        }, DOOR_TIMEOUT);

        context.effects().playSound(doorLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
        context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text("Двері створено.").color(NamedTextColor.AQUA));

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

        // Deferred - spirituality will be consumed after coordinates are entered and door is created
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

        // Consume spirituality NOW (deferred execution complete)
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності для створення двері!");
            return;
        }

        Location doorLoc = context.getCasterEyeLocation().add(context.getCasterLocation().getDirection().multiply(2));
        Vector direction = context.getCasterLocation().getDirection();

        DoorData door = new DoorData(doorLoc, DoorType.TELEPORT, casterId, target);
        activeDoors.put(casterId, door);

        createPersistentDoor(context, door, direction);
        startDoorMonitoring(context, door);

        context.scheduling().scheduleDelayed(() -> {
            if (!door.casterEntered && activeDoors.containsValue(door)) {
                activeDoors.remove(door.casterId);
                context.messaging().sendMessage(casterId, ChatColor.YELLOW + "Двері зникли через неактивність");
            }
        }, DOOR_TIMEOUT);

        context.effects().playSound(doorLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
        context.messaging().sendMessageToActionBar(casterId, Component.text("Двері створено.").color(NamedTextColor.AQUA));
    }

    private void startDoorMonitoring(IAbilityContext context, DoorData door) {
        context.events().subscribeToTemporaryEvent(door.casterId,
                PlayerMoveEvent.class,
                e -> !door.casterEntered && e.getTo() != null &&
                        e.getTo().distance(door.location) < 1.5,
                e -> handlePlayerEnterDoor(context, door, e.getPlayer().getUniqueId()),
                DOOR_TIMEOUT + 100
        );
    }

    private void handlePlayerEnterDoor(IAbilityContext context, DoorData door, UUID playerId) {
        // ВИПРАВЛЕННЯ СПАМУ: Перевіряємо, чи цей гравець вже увійшов у ці двері
        if (door.enteredPlayers.contains(playerId)) {
            return;
        }

        // Додаємо гравця в список тих, хто увійшов (щоб не спрацьовувало двічі)
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
        }
    }

    private void handleSpectatorEntry(IAbilityContext context, DoorData door, UUID playerId, boolean isCaster) {
        context.entity().setGameMode(playerId, GameMode.SPECTATOR);
        activeSpectators.add(playerId);

        context.effects().playSound(context.playerData().getCurrentLocation(playerId), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
        context.effects().spawnParticle(Particle.REVERSE_PORTAL, context.playerData().getCurrentLocation(playerId), 30, 0.5, 1, 0.5);

        if (!isCaster) {
            // Пасажир увійшов
            context.messaging().sendMessageToActionBar(playerId, Component.text(ChatColor.AQUA + "Ви увійшли у простір. Чекайте на провідника."));

            // Додаємо пасажира до сесії кастера
            sessionPassengers.computeIfAbsent(door.casterId, k -> ConcurrentHashMap.newKeySet())
                    .add(playerId);

            // Сповіщаємо кастера, якщо він онлайн
            Player caster = Bukkit.getPlayer(door.casterId);
            if (caster != null && caster.isOnline()) {
                context.messaging().sendMessageToActionBar(door.casterId, Component.text(context.playerData().getName(playerId) + " увійшов у ваші двері.").color(NamedTextColor.GREEN));
            }

        } else {
            // Кастер увійшов
            context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text("Подорожування активовано на 60 секунд").color(NamedTextColor.AQUA));
            context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text("Напишіть в чат exit, щоб завершити.").color(NamedTextColor.GREEN));

            // ЗАПУСК МЕХАНІЗМУ "ВЕСТИ ЗА РУКУ"
            startTetheringTask(context,playerId);

            context.scheduling().scheduleDelayed(() -> {
                if (activeSpectators.contains(context.getCasterId())) {
                    exitSpectatorMode(context,playerId);
                    context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text("Час спостереження вийшов.").color(NamedTextColor.YELLOW));
                }
            }, SPECTATOR_DURATION);
        }
    }

    // Метод, який постійно телепортує пасажирів до кастера
    private void startTetheringTask(IAbilityContext context, UUID playerId) {
        context.scheduling().scheduleRepeating(() -> {
            // Якщо кастер вже не в режимі - зупиняємо
            if (!activeSpectators.contains(context.getCasterId())) {
                return;
            }

            Set<UUID> passengers = sessionPassengers.get(context.getCasterId());
            if (passengers != null && !passengers.isEmpty()) {
                for (UUID passengerId : passengers) {
                    Player p = Bukkit.getPlayer(passengerId);
                    // Перевіряємо чи пасажир онлайн і чи він все ще в режимі
                    if (p != null && p.isOnline() && activeSpectators.contains(passengerId)) {
                        // Телепортуємо пасажира до кастера (трохи позаду або прямо в нього)
                        if (p.getWorld().equals(context.getCasterLocation().getWorld())) {
                            if (p.getLocation().distanceSquared(context.getCasterLocation()) > 1) {
                                p.teleport(context.getCasterLocation());
                            }
                        } else {
                            p.teleport(context.getCasterLocation());
                        }
                    }
                }
            }
        }, 0L, 1L); // Запуск кожного тіку (1L) для плавності
    }

    private void handleTeleportEntry(IAbilityContext context, DoorData door, UUID playerId, boolean isCaster) {
        context.entity().teleport(playerId, door.targetLocation);

        Location exitDoor = door.targetLocation.clone().add(0, 0.5, 0);
        createMysticalDoor(context, exitDoor, new Vector(1, 0, 0));

        context.effects().playSound(door.targetLocation, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
        context.effects().spawnParticle(Particle.REVERSE_PORTAL, door.targetLocation, 50, 1, 1, 1);

        context.messaging().sendMessageToActionBar(playerId, Component.text("Телепортація виконана!").color(NamedTextColor.GREEN));
    }

    private void exitSpectatorMode(IAbilityContext context, UUID playerId) {
        if (!activeSpectators.contains(playerId)) return;

        // Логіка виходу
        activeSpectators.remove(playerId);
        context.entity().setGameMode(playerId, GameMode.SURVIVAL);

        if (context.playerData().getEyeLocation(playerId).getBlock().getType().isSolid()) {
            context.entity().teleport(context.getCasterId(), context.getCasterEyeLocation().add(0, 1, 0));
            context.entity().teleport(playerId, context.playerData().getCurrentLocation(playerId).add(0, 1, 0));
            context.entity().teleport(context.getCasterId(), context.getCasterEyeLocation().add(0, 1, 0));
        }

        // Якщо виходить пасажир - видаляємо його зі списку пасажирів будь-якого кастера
        sessionPassengers.values().forEach(set -> set.remove(playerId));

        // Якщо виходить КАСТЕР - виганяємо всіх його пасажирів
        if (sessionPassengers.containsKey(playerId)) {
            Set<UUID> passengers = sessionPassengers.remove(playerId);
            if (passengers != null) {
                for (UUID passengerId : passengers) {
                    if (context.playerData().isOnline(playerId) && activeSpectators.contains(passengerId)) {
                        context.messaging().sendMessageToActionBar(playerId, Component.text("Провідник покинув простір.").color(NamedTextColor.YELLOW));
                        exitSpectatorMode(context, passengerId); // Рекурсивний виклик для пасажира
                        // Телепортуємо пасажира до точки виходу кастера
                        context.entity().teleport(passengerId, context.getCasterLocation());
                    }
                }
            }
        }
        context.cooldown().setCooldown(this, context.getCasterId());
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
            context.scheduleDelayed(() -> {
                double progress = finalFrame / 20.0;

                drawDoorFrame(context, door.location, right, width * progress, height * progress, portalDust);

                if (progress > 0.5) {
                    fillDoorPortal(context, door.location, right, finalDir,
                            width * progress, height * progress, glowDust);
                }

                if (finalFrame % 5 == 0) {
                    context.playSound(door.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
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
        // Зберігає ID всіх гравців, які увійшли в ці конкретні двері, щоб уникнути спаму
        final Set<UUID> enteredPlayers = new HashSet<>();

        DoorData(Location location, DoorType type, UUID casterId, Location targetLocation) {
            this.location = location;
            this.type = type;
            this.casterId = casterId;
            this.targetLocation = targetLocation;
        }
    }
}