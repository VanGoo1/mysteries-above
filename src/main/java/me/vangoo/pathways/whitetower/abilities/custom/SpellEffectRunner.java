package me.vangoo.pathways.whitetower.abilities.custom;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.spells.SpellRecipe;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Поведінка згенерованого заклинання: уся Bukkit-хореографія (партикли, raytrace, проджектайл,
 * телепорт, урон) в одному місці. Чистий рецепт {@link SpellRecipe} вирішує "що", раннер — "як".
 * <p>
 * Тут Bukkit дозволено: це шар ефектів, не правил. Раннер без стану — приймає рецепт і контекст.
 */
public final class SpellEffectRunner {

    private SpellEffectRunner() {
    }

    public static AbilityResult cast(SpellRecipe recipe, IAbilityContext context) {
        return switch (recipe.shape()) {
            case PROJECTILE -> projectile(recipe, context);
            case AOE -> aoe(recipe, context);
            case SELF -> self(recipe, context);
            case BUFF -> buff(recipe, context);
            case TELEPORT -> teleport(recipe, context);
        };
    }

    private static AbilityResult projectile(SpellRecipe recipe, IAbilityContext context) {
        final UUID casterId = context.getCasterId();
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null) return AbilityResult.failure("Caster not online.");

        final Particle particle = particleFor(recipe.shape());
        Location start = caster.getEyeLocation();
        Vector direction = start.getDirection();

        double speed = 1.5;
        double range = 40.0;

        context.effects().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.5f);

        createTrackedProjectile(context, casterId, start, direction, range, speed,
                (location) -> context.effects().spawnParticle(particle, location, 2, 0.05, 0.05, 0.05),
                (target) -> {
                    if (recipe.dealsDamage()) {
                        context.entity().damage(target.getUniqueId(), recipe.damage());
                        target.setNoDamageTicks(0); // Ігноруємо i-frames для спаму
                    }
                    context.effects().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
                    context.effects().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                    return true; // Зупинити снаряд
                });

        return AbilityResult.success();
    }

    private static AbilityResult aoe(SpellRecipe recipe, IAbilityContext context) {
        final UUID casterId = context.getCasterId();
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null) return AbilityResult.failure("Caster not online.");

        final Particle particle = particleFor(recipe.shape());
        Location center = caster.getLocation();

        context.effects().playSphereEffect(center.clone().add(0, 1, 0), recipe.radius(), particle, (int) (recipe.radius() * 10));
        context.effects().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        Collection<LivingEntity> targets = context.targeting().getNearbyEntities(recipe.radius());
        for (LivingEntity entity : targets) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) continue; // Не бити себе
            if (recipe.dealsDamage()) {
                context.entity().damage(entity.getUniqueId(), recipe.damage());
                Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.5).setY(0.2);
                entity.setVelocity(knockback);
            }
        }
        return AbilityResult.success();
    }

    private static AbilityResult self(SpellRecipe recipe, IAbilityContext context) {
        final UUID casterId = context.getCasterId();
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null) return AbilityResult.failure("Caster not online.");

        final Particle particle = particleFor(recipe.shape());
        if (recipe.heals()) {
            context.entity().heal(caster.getUniqueId(), recipe.heal());
        }

        context.effects().spawnParticle(particle, caster.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        context.effects().spawnParticle(Particle.HEART, caster.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3);
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        return AbilityResult.success();
    }

    private static AbilityResult buff(SpellRecipe recipe, IAbilityContext context) {
        final UUID casterId = context.getCasterId();
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null) return AbilityResult.failure("Caster not online.");

        PotionEffectType effect = toBukkit(recipe.buff());
        if (effect == null || recipe.durationTicks() <= 0) {
            return AbilityResult.failure("Це заклинання не має ефектів.");
        }

        final Particle particle = particleFor(recipe.shape());
        context.entity().applyPotionEffect(casterId, effect, recipe.durationTicks(), recipe.buffAmplifier());
        context.effects().playVortexEffect(caster.getLocation(), 2, 1, particle, 30);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GREEN + "Ефект накладено!"));

        return AbilityResult.success();
    }

    private static AbilityResult teleport(SpellRecipe recipe, IAbilityContext context) {
        final UUID casterId = context.getCasterId();
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null) return AbilityResult.failure("Caster not online.");

        final Particle particle = particleFor(recipe.shape());
        double reach = recipe.radius() <= 0 ? 10 : recipe.radius();

        Block targetBlock = caster.getTargetBlockExact((int) reach);
        Location targetLoc;
        if (targetBlock != null && targetBlock.getType() != Material.AIR) {
            targetLoc = targetBlock.getLocation().add(0.5, 1.0, 0.5);
        } else {
            targetLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(reach));
        }

        targetLoc.setYaw(caster.getLocation().getYaw());
        targetLoc.setPitch(caster.getLocation().getPitch());
        if (targetLoc.getBlock().getType().isSolid()) {
            targetLoc.add(0, 1, 0);
        }

        context.effects().spawnParticle(Particle.REVERSE_PORTAL, caster.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        context.effects().playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        context.entity().teleport(casterId, targetLoc);

        context.effects().spawnParticle(particle, targetLoc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        context.effects().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        return AbilityResult.success();
    }

    private static void createTrackedProjectile(
            IAbilityContext context,
            UUID casterId,
            Location start,
            Vector direction,
            double maxRange,
            double step,
            Consumer<Location> particleEffect,
            Predicate<LivingEntity> onHit
    ) {
        direction.normalize().multiply(step);
        final Location currentLocation = start.clone();
        final double maxRangeSquared = maxRange * maxRange;
        final Set<Integer> hitEntities = new HashSet<>();
        final BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = context.scheduling().scheduleRepeating(() -> {
            if (currentLocation.distanceSquared(start) > maxRangeSquared) {
                if (taskHolder[0] != null) taskHolder[0].cancel();
                return;
            }
            if (!currentLocation.getBlock().isPassable()) {
                context.effects().spawnParticle(Particle.EGG_CRACK, currentLocation, 10, 0.5, 0.5, 0.5);
                if (taskHolder[0] != null) taskHolder[0].cancel();
                return;
            }

            currentLocation.add(direction);
            particleEffect.accept(currentLocation.clone());

            Collection<Entity> nearbyRaw = currentLocation.getWorld().getNearbyEntities(currentLocation, 0.8, 0.8, 0.8);
            for (Entity entity : nearbyRaw) {
                if (entity.getUniqueId().equals(casterId)) continue;
                if (!(entity instanceof LivingEntity target)) continue;
                if (hitEntities.contains(entity.getEntityId())) continue;

                if (target.getBoundingBox().contains(currentLocation.toVector())) {
                    if (onHit.test(target)) {
                        if (taskHolder[0] != null) taskHolder[0].cancel();
                        return;
                    }
                    hitEntities.add(target.getEntityId());
                }
            }
        }, 0L, 1L);

        context.scheduling().scheduleDelayed(() -> {
            if (taskHolder[0] != null && !taskHolder[0].isCancelled()) {
                taskHolder[0].cancel();
            }
        }, 100L);
    }

    private static Particle particleFor(SpellRecipe.Shape shape) {
        return switch (shape) {
            case PROJECTILE -> Particle.FIREWORK;
            case AOE -> Particle.EXPLOSION;
            case TELEPORT -> Particle.PORTAL;
            case SELF -> Particle.HEART;
            case BUFF -> Particle.SOUL;
        };
    }

    private static PotionEffectType toBukkit(SpellRecipe.Buff buff) {
        if (buff == null) return null;
        return switch (buff) {
            case SPEED -> PotionEffectType.SPEED;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            case REGENERATION -> PotionEffectType.REGENERATION;
            case FIRE_RESISTANCE -> PotionEffectType.FIRE_RESISTANCE;
        };
    }
}
