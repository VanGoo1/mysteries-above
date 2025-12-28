package me.vangoo.domain.pathways.door.abilities;
import org.bukkit.Particle.DustOptions;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class DoorOpening extends ActiveAbility {

    private static final int MAX_THICKNESS = 10; // Максимальна товщина стіни
    private static final int ACTIVATION_RANGE = 2; // Як близько треба стояти до стіни
    private static final int COST = 50;
    private static final int COOLDOWN = 15;

    @Override
    public String getName() {
        return "Відкриття дверей";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Створіть примарні двері на перешкоді перед вами та пройдіть крізь стіну товщиною до "
                + MAX_THICKNESS + " блоків.";
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
        Location playerLoc = context.getCasterLocation();
        Vector dir = playerLoc.getDirection().setY(0).normalize(); // ігноруємо вертикаль

        // 1) Знайти блок-стіну перед гравцем
        Block entry = findWallInFront(playerLoc, dir);
        if (entry == null) {
            return AbilityResult.failure("Ви повинні стояти впритул до стіни.");
        }

        // 2) Знайти безпечну локацію за стіною
        Location exit = findExitLocation(entry, dir);
        if (exit == null) {
            // звук/повідомлення через контекст
            context.playSoundToCaster(Sound.BLOCK_CHEST_LOCKED, 1.0f, 0.5f);
            return AbilityResult.failure("Ця стіна занадто товста або за нею немає місця.");
        }
        Location entryCenter = entry.getLocation().add(0.5, 0, 0.5);
        long seed = Objects.hash(entry.getX(), entry.getY(), entry.getZ(), 12345);
        // 3) Візуальні/звукові ефекти (через методи контексту)
        playPhantomDoorEffect(context, entryCenter, dir, seed);
        playPhantomDoorEffect(context, exit.clone().add(0, 0, 0), dir, seed);

        // 4) Невелика затримка — потім телепортуємо гравця і робимо фінальні ефекти
        context.scheduleDelayed(() -> {
            context.playSound(exit, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
            context.playSound(exit, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.5f);

            context.teleport(context.getCasterId(), exit);

            // слабкий партикл виходу
            context.spawnParticle(Particle.FIREWORK, exit.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3);
            // невеликий кубічний ефект для "примарної рами"
        }, 2L); // 2 тіки для плавності

        return AbilityResult.success();
    }

    /**
     * Простий пошук першого твердого блоку в напрямку погляду в межах ACTIVATION_RANGE.
     */
    private Block findWallInFront(Location start, Vector dir) {
        // починаємо з 1 блока перед гравцем
        for (int i = 1; i <= ACTIVATION_RANGE; i++) {
            Location probe = start.clone().add(dir.clone().multiply(i));
            Block b = probe.getBlock();
            if (b.getType().isSolid()) return b;
        }
        return null;
    }

    /**
     * Просканувати крізь стіну (до MAX_THICKNESS) і знайти перший двоблоковий простір (ноги + голова).
     * Повертає центр блока (y - рівень ніг) з напрямком гляду встановленим на те ж direction.
     */
    private Location findExitLocation(Block entryBlock, Vector direction) {
        Location center = entryBlock.getLocation().add(0.5, 0, 0.5);

        for (int i = 1; i <= MAX_THICKNESS; i++) {
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

        // Попередні звуки
        context.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        context.playSoundToCaster(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.9f);

        // Підготовка позицій частинок сітки (для повторного використання)
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

        // Повторюване завдання через контекст
        context.scheduleRepeating(new Runnable() {
            int ticksLeft = durationTicks;

            @Override
            public void run() {
                if (ticksLeft-- <= 0) return;

                // Рамка дверей
                Location leftBottom = planeCenter.clone().add(right.clone().multiply(-halfWidth));
                Location rightBottom = planeCenter.clone().add(right.clone().multiply(halfWidth));
                Location leftTop = leftBottom.clone().add(0, height, 0);
                Location rightTop = rightBottom.clone().add(0, height, 0);

                context.playLineEffect(leftBottom, leftTop, Particle.END_ROD);
                context.playLineEffect(rightBottom, rightTop, Particle.END_ROD);
                context.playLineEffect(leftTop, rightTop, Particle.END_ROD);
                context.playLineEffect(leftBottom, rightBottom, Particle.END_ROD);

                // Частинки сітки
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
}
