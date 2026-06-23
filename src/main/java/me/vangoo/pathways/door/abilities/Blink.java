package me.vangoo.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

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
        UUID casterId = context.getCasterId();

        Location startLoc = context.playerData().getEyeLocation(casterId);
        if (startLoc == null || startLoc.getWorld() == null) {
            return AbilityResult.failure("Неможливо визначити позицію гравця");
        }

        Vector direction = startLoc.getDirection();
        World world = startLoc.getWorld();

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
            targetLocation = hitBlock.getLocation().add(0.5, 0, 0.5)
                    .add(direction.clone().multiply(-0.5));
        } else {
            targetLocation = startLoc.clone()
                    .add(direction.clone().multiply(MAX_DISTANCE));
        }

        Location safeLoc = findSafeLocation(targetLocation);
        if (safeLoc == null) {
            return AbilityResult.failure("Неможливо знайти безпечне місце");
        }

        safeLoc.setDirection(direction);

        Location oldLocation = context.playerData().getCurrentLocation(casterId);

        // 🔹 ефекти зникнення
        showDepartureEffects(oldLocation, context);
        context.effects().playSound(oldLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);

        // 🔹 телепорт
        context.entity().teleport(casterId, safeLoc);

        // 🔹 ефекти появи
        showArrivalEffects(safeLoc, context);
        context.effects().playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.2f);

        showTeleportTrail(oldLocation, safeLoc, context);

        return AbilityResult.success();
    }


    /**
     * Силует гравця що зникає
     */
    private void showDepartureEffects(Location loc, IAbilityContext context) {
        // Силует гравця (тіло + голова)
        for (int i = 0; i < 15; i++) {
            int delay = i;
            context.scheduling().scheduleDelayed(() -> {
                double fade = 1.0 - (delay / 15.0);

                // Ноги (0.0 - 0.5)
                drawPlayerPart(loc, 0.0, 0.5, 0.3, fade, context);

                // Тіло (0.5 - 1.5)
                drawPlayerPart(loc, 0.5, 1.5, 0.35, fade, context);

                // Руки
                drawPlayerPart(loc.clone().add(0.4, 0.8, 0), 0.8, 1.3, 0.15, fade, context);
                drawPlayerPart(loc.clone().add(-0.4, 0.8, 0), 0.8, 1.3, 0.15, fade, context);

                // Голова (1.5 - 1.8)
                drawPlayerPart(loc, 1.5, 1.8, 0.25, fade, context);
            }, i);
        }

        // Фінальний спалах
        context.scheduling().scheduleDelayed(() -> {
            context.effects().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        }, 15);
    }
    /**
     * Силует гравця що з'являється
     */
    /**
     * Силует гравця що з'являється
     */
    private void showArrivalEffects(Location loc, IAbilityContext context) {
        // Початковий спалах
        context.effects().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3);

        // Силует гравця що формується (від прозорого до яскравого)
        for (int i = 0; i < 15; i++) {
            int delay = i;
            context.scheduling().scheduleDelayed(() -> {
                double fade = delay / 15.0;

                // Ноги
                drawPlayerPart(loc, 0.0, 0.5, 0.3, fade, context);

                // Тіло
                drawPlayerPart(loc, 0.5, 1.5, 0.35, fade, context);

                // Руки
                drawPlayerPart(loc.clone().add(0.4, 0.8, 0), 0.8, 1.3, 0.15, fade, context);
                drawPlayerPart(loc.clone().add(-0.4, 0.8, 0), 0.8, 1.3, 0.15, fade, context);

                // Голова
                drawPlayerPart(loc, 1.5, 1.8, 0.25, fade, context);
            }, i);
        }
    }

    /**
     * Малює частину тіла гравця з ефектом затухання/появи
     */
    /**
     * Малює частину тіла гравця.
     * Використовує DUST для стабільності форми, пофарбований під SOUL_FIRE.
     */
    private void drawPlayerPart(Location baseLoc, double yStart, double yEnd, double width, double fade, IAbilityContext context) {
        World world = baseLoc.getWorld();
        if (world == null) return;

        // 1. СТВОРЕННЯ ЕКЗЕМПЛЯРУ DUST OPTIONS
        // Color.fromRGB(R, G, B) -> (50, 255, 255) це колір бірюзового вогню (Soul Fire)
        // 1.0f -> це розмір частинки
        Particle.DustOptions soulDust = new Particle.DustOptions(Color.fromRGB(50, 255, 255), 1.0f);

        for (double y = yStart; y <= yEnd; y += 0.2) {
            // Малюємо 8 точок навколо центру
            for (int angle = 0; angle < 360; angle += 45) {

                // Ефект зникнення: чим більший fade, тим більше точок пропускаємо
                if (Math.random() > fade) {
                    continue;
                }

                double rad = Math.toRadians(angle);
                double x = Math.cos(rad) * width;
                double z = Math.sin(rad) * width;

                Location particleLoc = baseLoc.clone().add(x, y, z);

                // 2. ВИКЛИК ЧЕРЕЗ WORLD (Стандартний API)
                // Ми використовуємо world.spawnParticle, тому що він точно приймає 'data' (soulDust)
                // count = 1
                // offset X, Y, Z = 0, 0, 0 (щоб не розліталось)
                // extra = 0
                // data = soulDust
                world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, soulDust);
            }
        }
    }
    /**
     * Швидка лінія між точками телепортації
     */
    private void showTeleportTrail(Location from, Location to, IAbilityContext context) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        // Швидка анімована лінія (20 кадрів)
        for (int i = 0; i < 20; i++) {
            int delay = i;
            context.scheduling().scheduleDelayed(() -> {
                double progress = (delay / 20.0);
                double currentDist = distance * progress;

                Location point = from.clone().add(direction.clone().multiply(currentDist));

                // Основна лінія - використовуємо ENCHANT замість WITCH
                context.effects().spawnParticle(
                        Particle.ENCHANT,
                        point.clone().add(0, 1, 0),
                        3,
                        0.05, 0.05, 0.05
                );

                // Портальні частинки
                context.effects().spawnParticle(
                        Particle.PORTAL,
                        point.clone().add(0, 1, 0),
                        5,
                        0.1, 0.1, 0.1
                );
            }, i);
        }
    }

    private Location findSafeLocation(Location target) {
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