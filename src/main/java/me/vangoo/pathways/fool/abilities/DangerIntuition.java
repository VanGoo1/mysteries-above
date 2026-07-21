package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.DangerPremonition;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sequence 9: Seer — Danger Intuition (Інтуїція небезпеки), сильно покращена.
 *
 * <p>Крім базових попереджень (ворожі моби, озброєні гравці, снаряди) дає:
 * <ul>
 *   <li>передчуття вхідного удару/пострілу до його приходу;</li>
 *   <li>бачення сущностей за дверима без їх відчинення (не Spirit Vision);</li>
 *   <li>зрідка — натяк на наступну дію ворога;</li>
 *   <li>авто-ухилення від удару, що був би смертельним (з шансом за Sequence).</li>
 * </ul>
 * Балансні числа — {@link DangerPremonition}; тут лише ефекти.
 */
public class DangerIntuition extends PermanentPassiveAbility {

    private static final double SCAN_RADIUS = 15.0;
    private static final double PROJECTILE_SCAN_RADIUS = 25.0;
    private static final long WARNING_COOLDOWN_MS = 3000; // 3 seconds between warnings per source

    private static final double DOOR_LOOK_RANGE = 3.0;
    private static final double DOOR_REVEAL_RADIUS = 4.5;
    private static final int DOOR_GLOW_TICKS = 40;
    private static final long DOOR_THROTTLE_MS = 2500;
    private static final double PREDICT_RANGE = 20.0;
    private static final long PREDICT_THROTTLE_MS = 4000;

    private final Map<UUID, Map<UUID, Long>> lastWarnings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDoorReveal = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPrediction = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDodge = new ConcurrentHashMap<>();
    // Підписка ухилення під ВЛАСНИМ ключем — стійка до unsubscribeAll інших здібностей.
    private final Map<UUID, UUID> dodgeSubscriptions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Інтуїція небезпеки";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int dodge = (int) Math.round(DangerPremonition.lethalDodgeChance(userSequence) * 100);
        return "Ви передчуваєте загрозу. Попереджає про ворогів і снаряди в радіусі " +
                (int) SCAN_RADIUS + " блоків, показує сущності за дверима без їх відчинення, " +
                "інколи вгадує наступну дію ворога та з шансом " + dodge +
                "% автоматично ухиляється від смертельного удару.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.put(casterId, 0);
        lastWarnings.put(casterId, new ConcurrentHashMap<>());

        // Авто-ухилення від летального удару — власна підписка під окремим ключем.
        UUID subKey = UUID.randomUUID();
        dodgeSubscriptions.put(casterId, subKey);
        context.events().subscribeToTemporaryEvent(subKey,
                EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId) && !e.isCancelled(),
                e -> tryLethalDodge(context, casterId, e),
                Integer.MAX_VALUE
        );
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.remove(casterId);
        lastWarnings.remove(casterId);
        lastDoorReveal.remove(casterId);
        lastPrediction.remove(casterId);
        lastDodge.remove(casterId);
        UUID subKey = dodgeSubscriptions.remove(casterId);
        if (subKey != null) context.events().unsubscribeAll(subKey);
    }

    private void tryLethalDodge(IAbilityContext context, UUID casterId, EntityDamageEvent e) {
        double currentHealth = context.playerData().getHealth(casterId);
        if (e.getFinalDamage() < currentHealth) return; // не смертельний

        Beyonder beyonder = context.getCasterBeyonder();
        if (beyonder == null) return;
        Sequence seq = beyonder.getSequence();
        long now = System.currentTimeMillis();

        Long last = lastDodge.get(casterId);
        if (last != null && now - last < DangerPremonition.lethalDodgeCooldownMillis(seq)) return;
        if (ThreadLocalRandom.current().nextDouble() > DangerPremonition.lethalDodgeChance(seq)) return;

        e.setCancelled(true);
        lastDodge.put(casterId, now);

        Location loc = context.playerData().getCurrentLocation(casterId);
        if (loc != null) {
            Vector look = loc.getDirection().setY(0);
            if (look.lengthSquared() < 0.01) look = new Vector(1, 0, 0);
            look.normalize();
            Vector side = new Vector(-look.getZ(), 0, look.getX());
            if (ThreadLocalRandom.current().nextBoolean()) side.multiply(-1);
            side.multiply(0.9).setY(0.35);
            context.entity().setVelocity(casterId, side);
            context.effects().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 20, 0.4, 0.5, 0.4);
            context.effects().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 10, 0.3, 0.4, 0.3);
        }
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.6f);
        context.messaging().sendMessageToActionBar(casterId,
                net.kyori.adventure.text.Component.text("✦ Передчуття врятувало вас!"));
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

        long now = System.currentTimeMillis();
        Map<UUID, Long> warnings = lastWarnings.computeIfAbsent(casterId, k -> new ConcurrentHashMap<>());

        scanThreats(context, casterId, casterLoc, warnings, now);
        revealBehindDoor(context, casterId, now);
        predictNextAction(context, casterId, now);

        // Cleanup old warnings
        if (counter % 200 == 0) {
            warnings.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
        }
    }

    private void scanThreats(IAbilityContext context, UUID casterId, Location casterLoc,
                             Map<UUID, Long> warnings, long now) {
        // 1. Scan hostile mobs targeting the caster
        for (Entity entity : context.targeting().getNearbyEntities(SCAN_RADIUS)) {
            if (entity instanceof Mob mob && mob.getTarget() != null
                    && mob.getTarget().getUniqueId().equals(casterId)) {
                UUID mobId = mob.getUniqueId();
                if (isOnCooldown(warnings, mobId, now)) continue;

                double distance = mob.getLocation().distance(casterLoc);
                String direction = getDirection(casterLoc, mob.getLocation());
                float volume = (float) Math.max(0.3, 1.0 - (distance / SCAN_RADIUS));

                context.effects().playSoundForPlayer(casterId,
                        Sound.BLOCK_NOTE_BLOCK_BELL, volume, 0.5f + volume);

                if (distance < 8) {
                    context.messaging().sendMessageToActionBar(casterId,
                            Component.text("⚠ Небезпека зі сторони " + direction +
                                    " (" + mob.getType().name().toLowerCase() + ", ~" + (int) distance + "б)!",
                                    NamedTextColor.RED));
                }
                warnings.put(mobId, now);
            }

            // 2. Scan armed players looking at caster
            if (entity instanceof Player player && !player.getUniqueId().equals(casterId)) {
                UUID playerId = player.getUniqueId();
                if (isOnCooldown(warnings, playerId, now)) continue;

                var mainHand = context.playerData().getMainHandItem(playerId);
                if (mainHand != null && isDangerousItem(mainHand.getType())
                        && isLookingAt(player.getEyeLocation(), casterLoc, 30)) {
                    String direction = getDirection(casterLoc, player.getLocation());
                    double dist = player.getLocation().distance(casterLoc);

                    context.effects().playSoundForPlayer(casterId,
                            Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.5f);
                    context.messaging().sendMessageToActionBar(casterId,
                            Component.text("⚠ " + player.getName() + " озброєний на " +
                                    direction + " (~" + (int) dist + "б)", NamedTextColor.YELLOW));
                    warnings.put(playerId, now);
                }
            }
        }

        // 3. Scan incoming projectiles — передчуття вхідного пострілу
        for (Entity entity : context.targeting().getNearbyEntities(PROJECTILE_SCAN_RADIUS)) {
            if (entity instanceof Projectile projectile) {
                UUID projId = projectile.getUniqueId();
                if (isOnCooldown(warnings, projId, now)) continue;

                if (isHeadingTowards(projectile.getLocation(), projectile.getVelocity(), casterLoc, 3.0)) {
                    context.effects().playSoundForPlayer(casterId,
                            Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
                    context.messaging().sendMessageToActionBar(casterId,
                            Component.text("⚡ Передчуття: снаряд летить у вас!", NamedTextColor.RED));
                    warnings.put(projId, now);
                }
            }
        }
    }

    /** Бачення сущностей за дверима/люком/ворітьми, на які дивиться Провидець. Не Spirit Vision. */
    private void revealBehindDoor(IAbilityContext context, UUID casterId, long now) {
        Long last = lastDoorReveal.get(casterId);
        if (last != null && now - last < DOOR_THROTTLE_MS) return;

        Player player = context.getCasterPlayer();
        if (player == null) return;

        Block target = player.getTargetBlockExact((int) DOOR_LOOK_RANGE);
        if (target == null || !isDoorLike(target.getType())) return;

        Location doorCenter = target.getLocation().add(0.5, 0.5, 0.5);
        List<LivingEntity> revealed = new java.util.ArrayList<>();
        for (LivingEntity le : context.targeting().getNearbyEntities(DOOR_REVEAL_RADIUS + DOOR_LOOK_RANGE)
                .stream().filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e).toList()) {
            if (le.getUniqueId().equals(casterId)) continue;
            if (le.getLocation().distance(doorCenter) <= DOOR_REVEAL_RADIUS) {
                context.glowing().setGlowing(le.getUniqueId(), casterId, ChatColor.RED, DOOR_GLOW_TICKS);
                revealed.add(le);
            }
        }

        if (!revealed.isEmpty()) {
            lastDoorReveal.put(casterId, now);
            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.7f, 1.4f);
            context.messaging().sendMessageToActionBar(casterId,
                    net.kyori.adventure.text.Component.text("👁 За дверима хтось є (" + revealed.size() + ")"));
        }
    }

    /** Зрідка — натяк на наступну дію ворога, за яким дивиться Провидець. */
    private void predictNextAction(IAbilityContext context, UUID casterId, long now) {
        Long last = lastPrediction.get(casterId);
        if (last != null && now - last < PREDICT_THROTTLE_MS) return;

        Beyonder beyonder = context.getCasterBeyonder();
        if (beyonder == null) return;

        var targetOpt = context.targeting().getTargetedEntity(PREDICT_RANGE);
        if (targetOpt.isEmpty()) return;
        LivingEntity target = targetOpt.get();
        if (target.getUniqueId().equals(casterId)) return;

        if (ThreadLocalRandom.current().nextDouble()
                > DangerPremonition.actionPredictionChance(beyonder.getSequence())) {
            return;
        }

        String hint;
        if (target instanceof Mob mob && mob.getTarget() != null
                && mob.getTarget().getUniqueId().equals(casterId)) {
            hint = "ціль зараз атакує";
        } else if (target.getVelocity().lengthSquared() > 0.05) {
            hint = "ціль рухається";
        } else {
            hint = "ціль вичікує";
        }

        lastPrediction.put(casterId, now);
        context.messaging().sendMessageToActionBar(casterId,
                net.kyori.adventure.text.Component.text("🔮 Передбачення: " + hint));
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

    private boolean isDoorLike(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE");
    }

    private boolean isLookingAt(Location eyeLoc, Location targetLoc, double maxAngle) {
        var direction = eyeLoc.getDirection().normalize();
        var toTarget = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double dot = direction.dot(toTarget);
        double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
        return angle <= maxAngle;
    }

    private boolean isHeadingTowards(Location projLoc, Vector velocity, Location targetLoc, double threshold) {
        if (velocity.lengthSquared() < 0.01) return false;
        var toTarget = targetLoc.toVector().subtract(projLoc.toVector());
        var velNorm = velocity.clone().normalize();
        double dot = velNorm.dot(toTarget.normalize());
        if (dot < 0.8) return false; // Not heading towards target
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
        lastDoorReveal.clear();
        lastPrediction.clear();
        lastDodge.clear();
        dodgeSubscriptions.clear();
    }
}
