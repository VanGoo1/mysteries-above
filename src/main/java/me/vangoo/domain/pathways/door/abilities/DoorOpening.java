
package me.vangoo.domain.pathways.door.abilities;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class DoorOpening extends ActiveAbility {

    private static final int BASE_THICKNESS = 10; // Базова товщина для 9 послідовності
    private static final int ACTIVATION_RANGE = 2;
    private static final int COST = 50;
    private static final int COOLDOWN = 15;

    // Для колективного порталу
    private static final int PORTAL_DURATION_TICKS = 100; // 5 секунд
    private static final double PORTAL_ACTIVATION_RADIUS = 3;

    // Зберігаємо активні портали: entry location -> exit location
    private static final Set<PortalData> activePortals = new HashSet<>();

    @Override
    public String getName() {
        return "Відкриття дверей";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        // Динамічно розраховуємо товщину для опису
        int currentMaxThickness = getMaxThickness(userSequence);

        if (userSequence.level() <= 7) {
            return "Створіть примарні двері на перешкоді перед вами та пройдіть крізь стіну товщиною до "
                    + currentMaxThickness + " блоків. З 7 послідовності портал стає доступним для всіх гравців поблизу.";
        } else {
            return "Створіть примарні двері на перешкоді перед вами (глибина до " + currentMaxThickness + "м). Портал залишається відкритим "
                    + (PORTAL_DURATION_TICKS / 20) + " секунд.";
        }
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    // Допоміжний метод для розрахунку дистанції на основі послідовності
    private int getMaxThickness(Sequence sequence) {
        // Використовуємо стратегію STRONG (+30% за рівень), оскільки це спеціалізація Шляху
        return scaleValue(BASE_THICKNESS, sequence, SequenceScaler.ScalingStrategy.STRONG);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Location playerLoc = context.getCasterLocation();
        Vector dir = playerLoc.getDirection().setY(0).normalize();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int sequenceLevel = sequence.level();

        // 1) Знайти блок-стіну перед гравцем
        Block entry = findWallInFront(playerLoc, dir);
        if (entry == null) {
            return AbilityResult.failure("Ви повинні стояти близько до стіни.");
        }

        // 2) Знайти безпечну локацію за стіною, використовуючи заскейлену дистанцію
        int currentMaxDist = getMaxThickness(sequence);
        Location exit = findExitLocation(entry, dir, currentMaxDist);

        if (exit == null) {
            context.playSoundToCaster(Sound.BLOCK_CHEST_LOCKED, 1.0f, 0.5f);
            return AbilityResult.failure("Ця стіна занадто товста (макс. " + currentMaxDist + " блоків) або за нею немає місця.");
        }

        Location entryCenter = entry.getLocation().add(0.5, 0, 0.5);
        long seed = Objects.hash(entry.getX(), entry.getY(), entry.getZ(), System.currentTimeMillis());

        // 3) Візуальні ефекти
        playPhantomDoorEffect(context, entryCenter, dir, seed);
        playPhantomDoorEffect(context, exit.clone(), dir, seed);

        // 4) Перевірка послідовності для колективного використання
        if (sequenceLevel <= 7) {
            // Покращена версія - створюємо портал для всіх
            createSharedPortal(context, entryCenter, exit, dir, seed);
            context.sendMessageToCaster(ChatColor.AQUA + "Портал створено! Інші гравці можуть пройти через нього");
        } else {
            // Стара версія - тільки для кастера
            context.scheduleDelayed(() -> {
                context.playSound(exit, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
                context.playSound(exit, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.5f);
                context.teleport(context.getCasterId(), exit);
                context.spawnParticle(Particle.FIREWORK, exit.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3);
            }, 2L);
        }

        return AbilityResult.success();
    }

    /**
     * Створює портал, доступний для всіх гравців поблизу
     */
    private void createSharedPortal(IAbilityContext context, Location entry, Location exit, Vector direction, long seed) {
        PortalData portal = new PortalData(entry, exit, direction);
        activePortals.add(portal);

        // Миттєво телепортуємо кастера
        context.scheduleDelayed(() -> {
            context.playSound(exit, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
            context.teleport(context.getCasterId(), exit);
            context.spawnParticle(Particle.FIREWORK, exit.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3);
        }, 2L);

        // Моніторинг гравців поблизу порталу
        BukkitTask monitorTask = context.scheduleRepeating(() -> {
            List<Player> nearbyPlayers = context.getNearbyPlayers(PORTAL_ACTIVATION_RADIUS);

            for (Player player : nearbyPlayers) {
                // Перевіряємо чи гравець близько до входу порталу
                if (player.getLocation().distance(entry) <= PORTAL_ACTIVATION_RADIUS) {
                    // Перевіряємо чи гравець ще не був телепортований цим порталом
                    if (!portal.hasTeleported(player.getUniqueId())) {
                        // Телепортуємо гравця
                        Location playerExit = exit.clone();
                        playerExit.setYaw(player.getLocation().getYaw());
                        playerExit.setPitch(player.getLocation().getPitch());

                        player.teleport(playerExit);
                        player.playSound(playerExit, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
                        player.spawnParticle(Particle.FIREWORK, playerExit.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3);
                        player.sendMessage(ChatColor.AQUA + "Ви пройшли через примарні двері!");

                        portal.markTeleported(player.getUniqueId());
                    }
                }
            }
        }, 0L, 5L); // Перевіряємо кожні 5 тіків

        // Закриваємо портал після закінчення часу
        context.scheduleDelayed(() -> {
            activePortals.remove(portal);
            monitorTask.cancel();

            // Фінальні ефекти закриття
            context.playSound(entry, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.8f);
            context.playSound(exit, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.8f);
            context.spawnParticle(Particle.SMOKE, entry, 20, 0.5, 1.0, 0.5);
            context.spawnParticle(Particle.SMOKE, exit, 20, 0.5, 1.0, 0.5);
        }, PORTAL_DURATION_TICKS);
    }

    private Block findWallInFront(Location start, Vector dir) {
        for (int i = 1; i <= ACTIVATION_RANGE; i++) {
            Location probe = start.clone().add(dir.clone().multiply(i));
            Block b = probe.getBlock();
            if (b.getType().isSolid()) return b;
        }
        return null;
    }

    // Оновлений метод з динамічною дистанцією
    private Location findExitLocation(Block entryBlock, Vector direction, int maxDist) {
        Location center = entryBlock.getLocation().add(0.5, 0, 0.5);

        for (int i = 1; i <= maxDist; i++) {
            Location check = center.clone().add(direction.clone().multiply(i));
            Block feet = check.getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();

            if (!feet.getType().isSolid() && !head.getType().isSolid()) {
                Location safe = feet.getLocation().add(0.5, 0, 0.5);
                safe.setDirection(direction);
                return safe;
            }
        }
        return null;
    }

    private void playPhantomDoorEffect(IAbilityContext context, Location center, Vector direction, long seed) {
        final double halfWidth = 0.6;
        final double height = 2.0;
        final double depth = 0.12;
        final int durationTicks = 80;
        final double stepW = 0.3;
        final double stepH = 0.3;
        final int depthLayers = 3;
        final double forwardTiny = 0.02;

        Vector dir = direction.clone().setY(0).normalize();
        if (dir.lengthSquared() == 0) dir = new Vector(0, 0, 1);

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location planeCenter = center.clone().add(dir.clone().multiply(-0.48));

        DustOptions blueDust = new DustOptions(Color.fromRGB(80, 220, 230), 0.8f);
        DustOptions purpleDust = new DustOptions(Color.fromRGB(170, 60, 200), 0.9f);

        final double w = halfWidth * 2.0;
        final int stepsX = Math.max(1, (int) Math.ceil(w / stepW));
        final int stepsY = Math.max(1, (int) Math.ceil(height / stepH));
        final double actualStepX = w / stepsX;
        final double actualStepY = height / stepsY;

        context.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        context.playSoundToCaster(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.9f);

        List<Location> particlePositions = new ArrayList<>();
        for (int layer = 0; layer < depthLayers; layer++) {
            double layerFrac = (depthLayers == 1) ? 0.5 : ((double) layer / (depthLayers - 1));
            double zOffset = layerFrac * depth - (depth / 2.0);
            for (int iy = 0; iy < stepsY; iy++) {
                double yCenter = (iy + 0.5) * actualStepY;
                for (int ix = 0; ix < stepsX; ix++) {
                    double xCenter = (ix + 0.5) * actualStepX;

                    Location base = planeCenter.clone()
                            .subtract(right.clone().multiply(halfWidth))
                            .subtract(dir.clone().multiply(depth / 2.0));

                    Location p = base.clone()
                            .add(right.clone().multiply(xCenter))
                            .add(0, yCenter, 0)
                            .add(dir.clone().multiply(zOffset + forwardTiny));

                    particlePositions.add(p);
                }
            }
        }

        context.scheduleRepeating(new Runnable() {
            int ticksLeft = durationTicks;

            @Override
            public void run() {
                if (ticksLeft-- <= 0) return;

                Location leftBottom = planeCenter.clone().add(right.clone().multiply(-halfWidth));
                Location rightBottom = planeCenter.clone().add(right.clone().multiply(halfWidth));
                Location leftTop = leftBottom.clone().add(0, height, 0);
                Location rightTop = rightBottom.clone().add(0, height, 0);

                context.playLineEffect(leftBottom, leftTop, Particle.END_ROD);
                context.playLineEffect(rightBottom, rightTop, Particle.END_ROD);
                context.playLineEffect(leftTop, rightTop, Particle.END_ROD);
                context.playLineEffect(leftBottom, rightBottom, Particle.END_ROD);

                World world = center.getWorld();
                if (world == null) return;

                for (Location p : particlePositions) {
                    long hash = Objects.hash(seed, Math.round(p.getX() * 1000), Math.round(p.getY() * 1000), Math.round(p.getZ() * 1000));
                    boolean isPurple = (Math.abs(hash) % 100) < 30;
                    double jitter = 0.05;
                    double xJ = (Math.random() - 0.5) * jitter;
                    double yJ = (Math.random() - 0.5) * jitter;
                    double zJ = (Math.random() - 0.5) * jitter;
                    Location pJittered = p.clone().add(xJ, yJ, zJ);
                    if (isPurple) {
                        world.spawnParticle(Particle.DUST, pJittered, 1, 0.0, 0.0, 0.0, 0.0, new DustOptions(purpleDust.getColor(), purpleDust.getSize()));
                    } else {
                        world.spawnParticle(Particle.DUST, pJittered, 1, 0.0, 0.0, 0.0, 0.0, new DustOptions(blueDust.getColor(), blueDust.getSize()));
                    }
                }
            }
        }, 0L, 1L);
    }

    /**
     * Клас для зберігання даних активного порталу
     */
    private static class PortalData {
        private final Location entry;
        private final Location exit;
        private final Vector direction;
        private final Set<UUID> teleportedPlayers;

        public PortalData(Location entry, Location exit, Vector direction) {
            this.entry = entry;
            this.exit = exit;
            this.direction = direction;
            this.teleportedPlayers = new HashSet<>();
        }

        public boolean hasTeleported(UUID playerId) {
            return teleportedPlayers.contains(playerId);
        }

        public void markTeleported(UUID playerId) {
            teleportedPlayers.add(playerId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PortalData that = (PortalData) o;
            return Objects.equals(entry, that.entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entry);
        }
    }
}
