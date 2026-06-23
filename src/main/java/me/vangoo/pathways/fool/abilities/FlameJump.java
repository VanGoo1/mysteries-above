package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sequence 7: Magician — Flame Jump (Стрибок крізь полум'я)
 *
 * Teleport to the nearest fire source in the direction the caster is looking.
 * Torches, campfires, soul fire, lanterns — all serve as anchors.
 */
public class FlameJump extends ActiveAbility {

    private static final int BASE_COST = 70;
    private static final int BASE_COOLDOWN = 6;
    private static final int SCAN_RADIUS = 30;
    private static final double CONE_ANGLE = 60.0; // degrees

    private static final Set<Material> FIRE_SOURCES = EnumSet.of(
            Material.TORCH, Material.SOUL_TORCH,
            Material.WALL_TORCH, Material.SOUL_WALL_TORCH,
            Material.FIRE, Material.SOUL_FIRE,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.LANTERN, Material.SOUL_LANTERN,
            Material.CANDLE, Material.JACK_O_LANTERN
    );

    @Override
    public String getName() {
        return "Стрибок крізь полум'я";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Телепортація до найближчого джерела вогню в напрямку погляду " +
                "(радіус " + SCAN_RADIUS + " блоків). Факели, вогнища, ліхтарі — все підходить.";
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
        Location eyeLoc = context.playerData().getEyeLocation(casterId);
        if (eyeLoc == null || eyeLoc.getWorld() == null) {
            return AbilityResult.failure("Неможливо визначити позицію");
        }

        Vector lookDir = eyeLoc.getDirection().normalize();
        Location casterLoc = context.playerData().getCurrentLocation(casterId);

        // Find best fire source in cone
        Block bestFire = null;
        double bestScore = Double.MAX_VALUE; // Lower is better (distance, weighted by angle)

        World world = eyeLoc.getWorld();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS / 2; y <= SCAN_RADIUS / 2; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    Block block = world.getBlockAt(
                            casterLoc.getBlockX() + x,
                            casterLoc.getBlockY() + y,
                            casterLoc.getBlockZ() + z
                    );

                    if (!FIRE_SOURCES.contains(block.getType())) continue;

                    Location blockLoc = block.getLocation().add(0.5, 0, 0.5);
                    double distance = casterLoc.distance(blockLoc);
                    if (distance < 2.0 || distance > SCAN_RADIUS) continue;

                    // Check if within cone
                    Vector toBlock = blockLoc.toVector().subtract(casterLoc.toVector()).normalize();
                    double dot = lookDir.dot(toBlock);
                    double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

                    if (angle <= CONE_ANGLE) {
                        // Score: prioritize direction alignment, then distance
                        double score = distance * (1.0 + angle / CONE_ANGLE);
                        if (score < bestScore) {
                            bestScore = score;
                            bestFire = block;
                        }
                    }
                }
            }
        }

        if (bestFire == null) {
            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
            return AbilityResult.failure("Немає джерел вогню поблизу в напрямку погляду!");
        }

        // Find safe location near fire
        Location fireCenter = bestFire.getLocation().add(0.5, 0, 0.5);
        Location safeLoc = findSafeLocationNear(fireCenter);
        if (safeLoc == null) {
            return AbilityResult.failure("Немає безпечного місця біля вогню");
        }
        safeLoc.setYaw(casterLoc.getYaw());
        safeLoc.setPitch(casterLoc.getPitch());

        // Departure effects
        Location oldLoc = casterLoc.clone();
        context.effects().spawnParticle(Particle.FLAME, oldLoc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3);
        context.effects().spawnParticle(Particle.SMOKE, oldLoc.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4);
        context.effects().playSound(oldLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.2f);

        // Teleport
        context.entity().teleport(casterId, safeLoc);

        // Arrival effects
        context.effects().spawnParticle(Particle.FLAME, safeLoc.clone().add(0, 0.5, 0), 25, 0.3, 0.3, 0.3);
        context.effects().spawnParticle(Particle.LAVA, safeLoc.clone().add(0, 1, 0), 10, 0.2, 0.3, 0.2);
        context.effects().playSound(safeLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        // Flame spiral arrival animation
        for (int i = 0; i < 10; i++) {
            int tick = i;
            context.scheduling().scheduleDelayed(() -> {
                double angle = tick * 0.6;
                double radius = 0.8 - (tick * 0.07);
                double px = Math.cos(angle) * radius;
                double pz = Math.sin(angle) * radius;
                context.effects().spawnParticle(Particle.FLAME,
                        safeLoc.clone().add(px, 0.2 + tick * 0.15, pz), 2, 0, 0, 0);
            }, tick);
        }

        return AbilityResult.success();
    }

    private Location findSafeLocationNear(Location center) {
        // Check center first
        if (isSafe(center)) return center;

        // Check adjacent blocks
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] offset : offsets) {
            Location check = center.clone().add(offset[0], 0, offset[1]);
            if (isSafe(check)) return check;

            // Try one block up
            Location checkUp = check.clone().add(0, 1, 0);
            if (isSafe(checkUp)) return checkUp;
        }

        return null;
    }

    private boolean isSafe(Location loc) {
        if (loc.getWorld() == null) return false;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return feet.isPassable() && head.isPassable() && ground.getType().isSolid()
                && feet.getType() != Material.LAVA && head.getType() != Material.LAVA;
    }
}
