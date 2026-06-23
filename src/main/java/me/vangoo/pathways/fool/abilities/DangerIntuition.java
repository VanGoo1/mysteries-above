package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 9: Seer — Danger Intuition (Інтуїція небезпеки)
 *
 * Permanent passive that alerts the Seer when hostile entities or armed players
 * approach. Provides directional warnings and projectile alerts.
 */
public class DangerIntuition extends PermanentPassiveAbility {

    private static final double SCAN_RADIUS = 15.0;
    private static final double PROJECTILE_SCAN_RADIUS = 25.0;
    private static final long WARNING_COOLDOWN_MS = 3000; // 3 seconds between warnings per source

    private final Map<UUID, Map<UUID, Long>> lastWarnings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Інтуїція небезпеки";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви інтуїтивно відчуваєте загрозу. Попереджає про ворожих мобів, " +
                "озброєних гравців та снаряди в радіусі " + (int) SCAN_RADIUS + " блоків.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.put(casterId, 0);
        lastWarnings.put(casterId, new ConcurrentHashMap<>());
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.remove(casterId);
        lastWarnings.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);

        // Process every 10 ticks (0.5 seconds)
        if (counter % 10 != 0) return;

        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        if (casterLoc == null) return;

        Map<UUID, Long> warnings = lastWarnings.computeIfAbsent(casterId, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();

        // 1. Scan hostile mobs targeting the caster
        for (Entity entity : context.targeting().getNearbyEntities(SCAN_RADIUS)) {
            if (entity instanceof Mob mob && mob.getTarget() != null) {
                if (mob.getTarget().getUniqueId().equals(casterId)) {
                    UUID mobId = mob.getUniqueId();
                    if (isOnCooldown(warnings, mobId, now)) continue;

                    double distance = mob.getLocation().distance(casterLoc);
                    String direction = getDirection(casterLoc, mob.getLocation());
                    float volume = (float) Math.max(0.3, 1.0 - (distance / SCAN_RADIUS));

                    context.effects().playSoundForPlayer(casterId,
                            Sound.BLOCK_NOTE_BLOCK_BELL, volume, 0.5f + volume);

                    if (distance < 8) {
                        context.messaging().sendMessage(casterId,
                                ChatColor.RED + "⚠ Небезпека зі сторони " + direction +
                                        " (" + mob.getType().name().toLowerCase() + ", ~" + (int) distance + "б)!");
                    }

                    warnings.put(mobId, now);
                }
            }

            // 2. Scan armed players looking at caster
            if (entity instanceof Player player && !player.getUniqueId().equals(casterId)) {
                UUID playerId = player.getUniqueId();
                if (isOnCooldown(warnings, playerId, now)) continue;

                var mainHand = context.playerData().getMainHandItem(playerId);
                if (mainHand != null && isDangerousItem(mainHand.getType())) {
                    // Check if player is looking at caster (within 30 degrees)
                    if (isLookingAt(player.getEyeLocation(), casterLoc, 30)) {
                        String direction = getDirection(casterLoc, player.getLocation());
                        double dist = player.getLocation().distance(casterLoc);

                        context.effects().playSoundForPlayer(casterId,
                                Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.5f);
                        context.messaging().sendMessage(casterId,
                                ChatColor.YELLOW + "⚠ " + player.getName() + " озброєний на " +
                                        direction + " (~" + (int) dist + "б)");

                        warnings.put(playerId, now);
                    }
                }
            }
        }

        // 3. Scan incoming projectiles (every 5 ticks via the 10-tick cycle)
        for (Entity entity : context.targeting().getNearbyEntities(PROJECTILE_SCAN_RADIUS)) {
            if (entity instanceof Projectile projectile) {
                UUID projId = projectile.getUniqueId();
                if (isOnCooldown(warnings, projId, now)) continue;

                // Check if projectile is heading towards caster
                if (isHeadingTowards(projectile.getLocation(), projectile.getVelocity(), casterLoc, 3.0)) {
                    context.effects().playSoundForPlayer(casterId,
                            Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
                    context.messaging().sendMessage(casterId,
                            ChatColor.RED + "⚡ УВАГА! Снаряд летить у вас!");

                    warnings.put(projId, now);
                }
            }
        }

        // Cleanup old warnings
        if (counter % 200 == 0) {
            warnings.entrySet().removeIf(e -> now - e.getValue() > 10000);
        }
    }

    private boolean isOnCooldown(Map<UUID, Long> warnings, UUID sourceId, long now) {
        Long lastWarn = warnings.get(sourceId);
        return lastWarn != null && (now - lastWarn) < WARNING_COOLDOWN_MS;
    }

    private boolean isDangerousItem(Material material) {
        String name = material.name();
        return name.contains("SWORD") || name.contains("AXE") || name.contains("BOW") ||
                name.contains("CROSSBOW") || name.contains("TRIDENT") || name.contains("MACE");
    }

    private boolean isLookingAt(Location eyeLoc, Location targetLoc, double maxAngle) {
        var direction = eyeLoc.getDirection().normalize();
        var toTarget = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double dot = direction.dot(toTarget);
        double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
        return angle <= maxAngle;
    }

    private boolean isHeadingTowards(Location projLoc, org.bukkit.util.Vector velocity, Location targetLoc, double threshold) {
        if (velocity.lengthSquared() < 0.01) return false;
        var toTarget = targetLoc.toVector().subtract(projLoc.toVector());
        var velNorm = velocity.clone().normalize();
        double dot = velNorm.dot(toTarget.normalize());
        if (dot < 0.8) return false; // Not heading towards target
        // Check closest approach distance
        double t = toTarget.dot(velNorm);
        var closestPoint = projLoc.toVector().add(velNorm.multiply(t));
        return closestPoint.distance(targetLoc.toVector()) < threshold;
    }

    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "Пд";
        if (angle >= 22.5 && angle < 67.5) return "ПдЗх";
        if (angle >= 67.5 && angle < 112.5) return "Зх";
        if (angle >= 112.5 && angle < 157.5) return "ПнЗх";
        if (angle >= 157.5 && angle < 202.5) return "Пн";
        if (angle >= 202.5 && angle < 247.5) return "ПнСх";
        if (angle >= 247.5 && angle < 292.5) return "Сх";
        return "ПдСх";
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
        lastWarnings.clear();
    }
}
