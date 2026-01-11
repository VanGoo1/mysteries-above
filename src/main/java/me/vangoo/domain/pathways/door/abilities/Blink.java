package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class Blink extends ActiveAbility {

    private static final double MAX_DISTANCE = 15.0;
    private static final int BASE_COST = 60;
    private static final int BASE_COOLDOWN = 8;

    @Override
    public String getName() {
        return "Блимання";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Миттєво переміщує вас вперед до " + (int)MAX_DISTANCE + " блоків у напрямку погляду.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCaster();
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection();
        World world = player.getWorld();

        // Ray trace для знаходження точки призначення
        RayTraceResult result = world.rayTraceBlocks(
                startLoc,
                direction,
                MAX_DISTANCE,
                FluidCollisionMode.NEVER,
                true
        );

        Location targetLocation;

        if (result != null && result.getHitBlock() != null) {
            Block hitBlock = result.getHitBlock();
            targetLocation = hitBlock.getLocation().add(0.5, 0, 0.5);
            Vector offset = direction.clone().multiply(-0.5);
            targetLocation.add(offset);
        } else {
            targetLocation = startLoc.clone().add(direction.multiply(MAX_DISTANCE));
        }

        Location safeLoc = findSafeLocation(targetLocation, world);

        if (safeLoc == null) {
            return AbilityResult.failure("Неможливо знайти безпечне місце для телепортації");
        }

        safeLoc.setDirection(player.getLocation().getDirection());

        Location oldLocation = player.getLocation().clone();

        // Ефекти зникнення
        showDepartureEffects(oldLocation, context);
        context.playSound(oldLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);

        // Телепортація
        context.teleport(player.getUniqueId(), safeLoc);

        // Ефекти появи
        showArrivalEffects(safeLoc, context);
        context.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.2f);

        // Швидка лінія між точками
        showTeleportTrail(oldLocation, safeLoc, context);

        // Система автоматично списує духовність та встановлює кулдаун
        return AbilityResult.success();
    }

    /**
     * Силует гравця що зникає
     */
    private void showDepartureEffects(Location loc, IAbilityContext context) {
        World world = loc.getWorld();

        // Силует гравця (тіло + голова)
        for (int i = 0; i < 15; i++) {
            int delay = i;
            context.scheduleDelayed(() -> {
                double fade = 1.0 - (delay / 15.0);

                // Ноги (0.0 - 0.5)
                drawPlayerPart(world, loc, 0.0, 0.5, 0.3, fade);

                // Тіло (0.5 - 1.5)
                drawPlayerPart(world, loc, 0.5, 1.5, 0.35, fade);

                // Руки
                drawPlayerPart(world, loc.clone().add(0.4, 0.8, 0), 0.8, 1.3, 0.15, fade);
                drawPlayerPart(world, loc.clone().add(-0.4, 0.8, 0), 0.8, 1.3, 0.15, fade);

                // Голова (1.5 - 1.8)
                drawPlayerPart(world, loc, 1.5, 1.8, 0.25, fade);
            }, i);
        }

        // Фінальний спалах
        context.scheduleDelayed(() -> {
            world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 15, 0.2, 0.4, 0.2, 0.05);
        }, 15);
    }

    /**
     * Силует гравця що з'являється
     */
    private void showArrivalEffects(Location loc, IAbilityContext context) {
        World world = loc.getWorld();

        // Початковий спалах
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.15);

        // Силует гравця що формується (від прозорого до яскравого)
        for (int i = 0; i < 15; i++) {
            int delay = i;
            context.scheduleDelayed(() -> {
                double fade = delay / 15.0;

                // Ноги
                drawPlayerPart(world, loc, 0.0, 0.5, 0.3, fade);

                // Тіло
                drawPlayerPart(world, loc, 0.5, 1.5, 0.35, fade);

                // Руки
                drawPlayerPart(world, loc.clone().add(0.4, 0.8, 0), 0.8, 1.3, 0.15, fade);
                drawPlayerPart(world, loc.clone().add(-0.4, 0.8, 0), 0.8, 1.3, 0.15, fade);

                // Голова
                drawPlayerPart(world, loc, 1.5, 1.8, 0.25, fade);
            }, i);
        }
    }

    /**
     * Малює частину тіла гравця з ефектом затухання/появи
     */
    private void drawPlayerPart(World world, Location baseLoc, double yStart, double yEnd, double width, double fade) {
        for (double y = yStart; y <= yEnd; y += 0.15) {
            // Кути силуету
            for (int angle = 0; angle < 360; angle += 90) {
                double rad = Math.toRadians(angle);
                double x = Math.cos(rad) * width;
                double z = Math.sin(rad) * width;

                Location particleLoc = baseLoc.clone().add(x, y, z);

                // Основні частинки (менше при зникненні)
                int count = (int)(3 * fade);
                if (count > 0) {
                    world.spawnParticle(
                            Particle.SOUL_FIRE_FLAME,
                            particleLoc,
                            count,
                            0.05, 0.05, 0.05,
                            0.01
                    );
                }
            }
        }
    }

    /**
     * Швидка лінія між точками телепортації
     */
    private void showTeleportTrail(Location from, Location to, IAbilityContext context) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        // Швидка анімована лінія (20 кадрів)
        for (int i = 0; i < 20; i++) {
            int delay = i;
            context.scheduleDelayed(() -> {
                double progress = (delay / 20.0);
                double currentDist = distance * progress;

                Location point = from.clone().add(direction.clone().multiply(currentDist));

                // Основна лінія - використовуємо ENCHANT замість WITCH
                world.spawnParticle(
                        Particle.ENCHANT,
                        point.clone().add(0, 1, 0),
                        3,
                        0.05, 0.05, 0.05,
                        0
                );

                // Портальні частинки
                world.spawnParticle(
                        Particle.PORTAL,
                        point.clone().add(0, 1, 0),
                        5,
                        0.1, 0.1, 0.1,
                        0.3
                );
            }, i);
        }
    }

    private Location findSafeLocation(Location target, World world) {
        if (isSafeLocation(target)) {
            return target;
        }

        for (int dy = 0; dy <= 3; dy++) {
            Location down = target.clone().subtract(0, dy, 0);
            if (isSafeLocation(down)) {
                return down;
            }

            if (dy > 0) {
                Location up = target.clone().add(0, dy, 0);
                if (isSafeLocation(up)) {
                    return up;
                }
            }
        }

        return null;
    }

    private boolean isSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        Block feetBlock = loc.getBlock();
        Block headBlock = feetBlock.getRelative(0, 1, 0);
        Block groundBlock = feetBlock.getRelative(0, -1, 0);

        if (!feetBlock.isPassable() || !headBlock.isPassable()) {
            return false;
        }

        if (!groundBlock.getType().isSolid()) {
            return false;
        }

        Material feetMaterial = feetBlock.getType();
        Material headMaterial = headBlock.getType();

        if (feetMaterial == Material.LAVA || feetMaterial == Material.FIRE ||
                headMaterial == Material.LAVA || headMaterial == Material.FIRE) {
            return false;
        }

        return true;
    }
}