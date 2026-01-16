package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
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
        Player caster = context.getCasterPlayer();

        // Перевірка: Якщо гравець вже у спектаторі і використовує предмет - теж викидаємо
        if (activeSpectators.contains(caster.getUniqueId())) {
            exitSpectatorMode(context, caster);
            return AbilityResult.successWithMessage(ChatColor.YELLOW + "Ви завершили подорожування");
        }

        if (caster.isSneaking()) {
            return handleTeleportMode(context, caster);
        } else {
            return handleSpectatorMode(context, caster);
        }
    }

    @Override
    protected void preExecution(IAbilityContext context) {
        // 1. Підписка на вихід гравців з серверу
        context.subscribeToEvent(
                PlayerQuitEvent.class,
                e -> activeSpectators.contains(e.getPlayer().getUniqueId()),
                e -> handlePlayerQuit(e.getPlayer()),
                Integer.MAX_VALUE
        );

        // 2. Підписка на exit для виходу з режиму спостереження
        context.subscribeToEvent(
                AsyncPlayerChatEvent.class,
                e -> activeSpectators.contains(e.getPlayer().getUniqueId()) &&
                        e.getMessage().equalsIgnoreCase("exit"),
                e -> {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("Mysteries-Above"),
                            () -> exitSpectatorMode(context, e.getPlayer())
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

    private void handlePlayerQuit(Player player) {
        if (activeSpectators.contains(player.getUniqueId())) {
            activeSpectators.remove(player.getUniqueId());
            player.setGameMode(GameMode.SURVIVAL);

            // Очистка сесії, якщо вийшов кастер
            sessionPassengers.remove(player.getUniqueId());
        }
    }

    private AbilityResult handleSpectatorMode(IAbilityContext context, Player caster) {
        Location doorLoc = caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(2));
        Vector direction = caster.getLocation().getDirection();

        DoorData door = new DoorData(doorLoc, DoorType.SPECTATOR, caster.getUniqueId(), null);
        activeDoors.put(caster.getUniqueId(), door);

        createPersistentDoor(context, door, direction);
        startDoorMonitoring(context, door);

        // Автоматичне видалення двері через таймаут
        context.scheduleDelayed(() -> {
            if (!door.casterEntered && activeDoors.containsValue(door)) {
                activeDoors.remove(door.casterId);
                caster.sendMessage(ChatColor.YELLOW + "Двері зникли через неактивність");
            }
        }, DOOR_TIMEOUT);

        context.playSound(doorLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
        caster.sendMessage(ChatColor.AQUA + "Двері створено.");

        return AbilityResult.success();
    }

    private AbilityResult handleTeleportMode(IAbilityContext context, Player caster) {
        awaitingCoordinates.add(caster.getUniqueId());

        caster.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        caster.sendMessage(ChatColor.YELLOW + " Введіть координати телепортації:");
        caster.sendMessage(ChatColor.GRAY + " Формат: X Y Z");
        caster.sendMessage(ChatColor.GRAY + " Приклад: 100 64 -200");
        caster.sendMessage(ChatColor.DARK_GRAY + " Макс. відстань: " + MAX_TELEPORT_DISTANCE + " блоків");
        caster.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");

        context.subscribeToEvent(
                AsyncPlayerChatEvent.class,
                e -> e.getPlayer().getUniqueId().equals(caster.getUniqueId()) &&
                        awaitingCoordinates.contains(caster.getUniqueId()),
                e -> {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("Mysteries-Above"),
                            () -> handleCoordinatesInput(context, caster, e.getMessage())
                    );
                },
                600
        );

        // Deferred - spirituality will be consumed after coordinates are entered and door is created
        return AbilityResult.deferred();
    }

    private void handleCoordinatesInput(IAbilityContext context, Player caster, String input) {
        if (!awaitingCoordinates.contains(caster.getUniqueId())) {
            return;
        }

        awaitingCoordinates.remove(caster.getUniqueId());

        try {
            String[] parts = input.trim().split("\\s+");
            if (parts.length != 3) {
                caster.sendMessage(ChatColor.RED + "Невірний формат! Використовуйте: X Y Z");
                return;
            }

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            Location currentLoc = caster.getLocation();
            Location targetLoc = new Location(caster.getWorld(), x, y, z);

            double distance = currentLoc.distance(targetLoc);
            if (distance > MAX_TELEPORT_DISTANCE) {
                caster.sendMessage(ChatColor.RED + "Занадто далеко! Відстань: " +
                        String.format("%.0f", distance) + " > " + MAX_TELEPORT_DISTANCE);
                return;
            }

            createTeleportDoor(context, caster, targetLoc);

        } catch (NumberFormatException e) {
            caster.sendMessage(ChatColor.RED + "Невірні координати! Використовуйте числа.");
        }
    }

    private void createTeleportDoor(IAbilityContext context, Player caster, Location target) {
        Beyonder casterBeyonder = context.getCasterBeyonder();

        // Consume spirituality NOW (deferred execution complete)
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            caster.sendMessage(ChatColor.RED + "Недостатньо духовності для створення двері!");
            return;
        }

        Location doorLoc = caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(2));
        Vector direction = caster.getLocation().getDirection();

        DoorData door = new DoorData(doorLoc, DoorType.TELEPORT, caster.getUniqueId(), target);
        activeDoors.put(caster.getUniqueId(), door);

        createPersistentDoor(context, door, direction);
        startDoorMonitoring(context, door);

        context.scheduleDelayed(() -> {
            if (!door.casterEntered && activeDoors.containsValue(door)) {
                activeDoors.remove(door.casterId);
                caster.sendMessage(ChatColor.YELLOW + "Двері зникли через неактивність");
            }
        }, DOOR_TIMEOUT);

        context.playSound(doorLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
        caster.sendMessage(ChatColor.AQUA + "Двері створено.");
    }

    private void startDoorMonitoring(IAbilityContext context, DoorData door) {
        context.subscribeToEvent(
                PlayerMoveEvent.class,
                e -> !door.casterEntered && e.getTo() != null &&
                        e.getTo().distance(door.location) < 1.5,
                e -> handlePlayerEnterDoor(context, door, e.getPlayer()),
                DOOR_TIMEOUT + 100
        );
    }

    private void handlePlayerEnterDoor(IAbilityContext context, DoorData door, Player player) {
        // ВИПРАВЛЕННЯ СПАМУ: Перевіряємо, чи цей гравець вже увійшов у ці двері
        if (door.enteredPlayers.contains(player.getUniqueId())) {
            return;
        }

        // Додаємо гравця в список тих, хто увійшов (щоб не спрацьовувало двічі)
        door.enteredPlayers.add(player.getUniqueId());

        boolean isCaster = player.getUniqueId().equals(door.casterId);

        if (door.type == DoorType.SPECTATOR) {
            handleSpectatorEntry(context, door, player, isCaster);
        } else {
            handleTeleportEntry(context, door, player, isCaster);
        }

        if (isCaster) {
            door.casterEntered = true;
            activeDoors.remove(door.casterId);
        }
    }

    private void handleSpectatorEntry(IAbilityContext context, DoorData door, Player player, boolean isCaster) {
        player.setGameMode(GameMode.SPECTATOR);
        activeSpectators.add(player.getUniqueId());

        context.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
        context.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 30, 0.5, 1, 0.5);

        if (!isCaster) {
            // Пасажир увійшов
            player.sendMessage(ChatColor.AQUA + "Ви увійшли у простір. Чекайте на провідника.");

            // Додаємо пасажира до сесії кастера
            sessionPassengers.computeIfAbsent(door.casterId, k -> ConcurrentHashMap.newKeySet())
                    .add(player.getUniqueId());

            // Сповіщаємо кастера, якщо він онлайн
            Player caster = Bukkit.getPlayer(door.casterId);
            if (caster != null && caster.isOnline()) {
                caster.sendMessage(ChatColor.GREEN + player.getName() + " увійшов у ваші двері.");
            }

        } else {
            // Кастер увійшов
            player.sendMessage(ChatColor.AQUA + "Подорожування активовано на 60 секунд");
            player.sendMessage(ChatColor.GREEN + "Напишіть в чат exit, щоб завершити.");

            // ЗАПУСК МЕХАНІЗМУ "ВЕСТИ ЗА РУКУ"
            startTetheringTask(context, player);

            context.scheduleDelayed(() -> {
                if (activeSpectators.contains(player.getUniqueId())) {
                    exitSpectatorMode(context, player);
                    player.sendMessage(ChatColor.YELLOW + "Час спостереження вийшов.");
                }
            }, SPECTATOR_DURATION);
        }
    }

    // Метод, який постійно телепортує пасажирів до кастера
    private void startTetheringTask(IAbilityContext context, Player caster) {
        context.scheduleRepeating(() -> {
            // Якщо кастер вже не в режимі - зупиняємо
            if (!activeSpectators.contains(caster.getUniqueId())) {
                return;
            }

            Set<UUID> passengers = sessionPassengers.get(caster.getUniqueId());
            if (passengers != null && !passengers.isEmpty()) {
                for (UUID passengerId : passengers) {
                    Player p = Bukkit.getPlayer(passengerId);
                    // Перевіряємо чи пасажир онлайн і чи він все ще в режимі
                    if (p != null && p.isOnline() && activeSpectators.contains(passengerId)) {
                        // Телепортуємо пасажира до кастера (трохи позаду або прямо в нього)
                        if (p.getWorld().equals(caster.getWorld())) {
                            if (p.getLocation().distanceSquared(caster.getLocation()) > 1) {
                                p.teleport(caster.getLocation());
                            }
                        } else {
                            p.teleport(caster.getLocation());
                        }
                    }
                }
            }
        }, 0L, 1L); // Запуск кожного тіку (1L) для плавності
    }

    private void handleTeleportEntry(IAbilityContext context, DoorData door, Player player, boolean isCaster) {
        player.teleport(door.targetLocation);

        Location exitDoor = door.targetLocation.clone().add(0, 0.5, 0);
        createMysticalDoor(context, exitDoor, new Vector(1, 0, 0));

        context.playSound(door.targetLocation, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
        context.spawnParticle(Particle.REVERSE_PORTAL, door.targetLocation, 50, 1, 1, 1);

        player.sendMessage(ChatColor.GREEN + "Телепортація виконана!");
    }

    private void exitSpectatorMode(IAbilityContext context, Player player) {
        if (!activeSpectators.contains(player.getUniqueId())) return;

        // Логіка виходу
        activeSpectators.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);

        if (player.getLocation().getBlock().getType().isSolid()) {
            player.teleport(player.getLocation().add(0, 1, 0));
        }

        // Якщо виходить пасажир - видаляємо його зі списку пасажирів будь-якого кастера
        sessionPassengers.values().forEach(set -> set.remove(player.getUniqueId()));

        // Якщо виходить КАСТЕР - виганяємо всіх його пасажирів
        if (sessionPassengers.containsKey(player.getUniqueId())) {
            Set<UUID> passengers = sessionPassengers.remove(player.getUniqueId());
            if (passengers != null) {
                for (UUID passengerId : passengers) {
                    Player p = Bukkit.getPlayer(passengerId);
                    if (p != null && p.isOnline() && activeSpectators.contains(passengerId)) {
                        p.sendMessage(ChatColor.YELLOW + "Провідник покинув простір.");
                        exitSpectatorMode(context, p); // Рекурсивний виклик для пасажира
                        // Телепортуємо пасажира до точки виходу кастера
                        p.teleport(player.getLocation());
                    }
                }
            }
        }
        context.setCooldown(this, getCooldown(context.getCasterBeyonder().getSequence()));
        Location exitLoc = player.getLocation();
        createMysticalDoor(context, exitLoc, new Vector(0, 0, 1));

        context.playSound(exitLoc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);
        context.spawnParticle(Particle.REVERSE_PORTAL, exitLoc, 30, 0.5, 1, 0.5);
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
        context.scheduleRepeating(() -> {
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
            context.scheduleDelayed(() -> {
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