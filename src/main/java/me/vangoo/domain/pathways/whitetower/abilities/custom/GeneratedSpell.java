package me.vangoo.domain.pathways.whitetower.abilities.custom;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class GeneratedSpell extends ActiveAbility {

    public enum EffectType {
        PROJECTILE, AOE, SELF, BUFF, TELEPORT
    }

    private final AbilityIdentity identity;
    private final String name;
    private final String description;
    private final int spiritualityCost;
    private final int cooldown;

    private final EffectType effectType;
    private final Particle particle;
    private final double damage;
    private final double radius;
    private final double heal;
    private final int duration;
    private final PotionEffectType potionEffect;
    private final int potionAmplifier;

    public GeneratedSpell(AbilityIdentity identity, String name, String description,
                          int spiritualityCost, int cooldown,
                          EffectType effectType, Particle particle, double damage,
                          double radius, double heal,
                          int duration, PotionEffectType potionEffect, int potionAmplifier) {
        this.identity = identity;
        this.name = name;
        this.description = description;
        this.spiritualityCost = spiritualityCost;
        this.cooldown = cooldown;
        this.effectType = effectType;
        this.particle = particle;
        this.damage = damage;
        this.radius = radius;
        this.heal = heal;
        this.duration = duration;
        this.potionEffect = potionEffect;
        this.potionAmplifier = potionAmplifier;
    }

    // Getters для infrastructure layer (серіалізації)
    public EffectType getEffectType() {
        return effectType;
    }

    public Particle getParticle() {
        return particle;
    }

    public double getDamage() {
        return damage;
    }

    public double getRadius() {
        return radius;
    }

    public double getHeal() {
        return heal;
    }

    public int getDuration() {
        return duration;
    }

    public PotionEffectType getPotionEffect() {
        return potionEffect;
    }

    public int getPotionAmplifier() {
        return potionAmplifier;
    }

    @Override
    public AbilityIdentity getIdentity() {
        return identity;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return description;
    }

    @Override
    public int getSpiritualityCost() {
        return spiritualityCost;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return cooldown;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        switch (effectType) {
            case PROJECTILE:
                return executeProjectile(context);
            case AOE:
                return executeAoe(context);
            case SELF:
                return executeSelf(context);
            case BUFF:
                return executeBuff(context);
            case TELEPORT:
                return executeTeleport(context);
            default:
                return AbilityResult.failure("Помилка структури заклинання.");
        }
    }

    private AbilityResult executeProjectile(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        Location start = caster.getEyeLocation();
        Vector direction = start.getDirection();

        // Швидкість снаряду
        double speed = 1.5;
        double range = 40.0;

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.5f);

        createTrackedProjectile(context, start, direction, range, speed,
                (location) -> {
                    // Візуал польоту
                    location.getWorld().spawnParticle(particle, location, 2, 0.05, 0.05, 0.05, 0);
                    if (particle == Particle.FLAME) {
                        location.getWorld().spawnParticle(Particle.SMOKE, location, 1, 0, 0, 0, 0);
                    }
                },
                (target) -> {
                    // Логіка влучання
                    if (damage > 0) {
                        context.damage(target.getUniqueId(), damage);
                        target.setNoDamageTicks(0); // Ігноруємо i-frames для спаму
                    }
                    if (particle == Particle.FLAME) {
                        target.setFireTicks(60);
                    }

                    target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                    return true; // Зупинити снаряд
                }
        );

        return AbilityResult.success();
    }

    private AbilityResult executeAoe(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        Location center = caster.getLocation();

        // Ефект розширення сфери
        context.playSphereEffect(center.add(0, 1, 0), radius, particle, (int) (radius * 10));
        context.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        Collection<LivingEntity> targets = context.getNearbyEntities(radius);
        if (targets.isEmpty()) {
            return AbilityResult.success(); // Нікого немає, але мана витрачена
        }

        for (LivingEntity entity : targets) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) continue; // Не бити себе

            if (damage > 0) {
                context.damage(entity.getUniqueId(), damage);
                // Відштовхування від центру
                Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.5).setY(0.2);
                entity.setVelocity(knockback);
            }
        }
        return AbilityResult.success();
    }

    private AbilityResult executeSelf(IAbilityContext context) {
        Player caster = context.getCasterPlayer();

        if (heal > 0) {
            context.heal(caster.getUniqueId(), heal);
        }

        // Візуал
        context.spawnParticle(particle, caster.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        context.spawnParticle(Particle.HEART, caster.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3);
        context.playSoundToCaster(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        return AbilityResult.success();
    }

    private AbilityResult executeBuff(IAbilityContext context) {
        Player caster = context.getCasterPlayer();

        if (potionEffect != null && duration > 0) {
            caster.addPotionEffect(new PotionEffect(potionEffect, duration, potionAmplifier));
            context.playVortexEffect(caster.getLocation(), 2, 1, particle, 30);
            context.playSoundToCaster(Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

            context.sendMessageToActionBar(caster,
                    net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Ефект накладено!"));
        } else {
            return AbilityResult.failure("Це заклинання не має ефектів.");
        }
        return AbilityResult.success();
    }

    private AbilityResult executeTeleport(IAbilityContext context) {
        Player caster = context.getCasterPlayer();

        // Raytrace для безпечного телепорту
        Block targetBlock = caster.getTargetBlockExact((int) (radius <= 0 ? 10 : radius)); // radius тут як дальність
        Location targetLoc;

        if (targetBlock != null && targetBlock.getType() != Material.AIR) {
            targetLoc = targetBlock.getLocation().add(0.5, 1.0, 0.5);
        } else {
            // Якщо блок не знайдено (небо), беремо точку в повітрі
            targetLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply((radius <= 0 ? 10 : radius)));
        }

        // Корекція напрямку погляду (зберігаємо куди дивився)
        targetLoc.setYaw(caster.getLocation().getYaw());
        targetLoc.setPitch(caster.getLocation().getPitch());

        // Перевірка на застрягання
        if (targetLoc.getBlock().getType().isSolid()) {
            targetLoc.add(0, 1, 0); // Підняти, якщо в ногах блок
        }

        context.spawnParticle(Particle.REVERSE_PORTAL, caster.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        context.playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        caster.teleport(targetLoc);

        context.spawnParticle(particle, targetLoc.add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        context.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        return AbilityResult.success();
    }

    private void createTrackedProjectile(
            IAbilityContext context,
            Location start,
            Vector direction,
            double maxRange,
            double step,
            Consumer<Location> particleEffect,
            Predicate<LivingEntity> onHit
    ) {
        direction.normalize().multiply(step); // Крок переміщення
        final Location currentLocation = start.clone();
        final double maxRangeSquared = maxRange * maxRange;
        final Player caster = context.getCasterPlayer();

        // Для оптимізації, щоб не бити одну ціль двічі одним снарядом
        Set<Integer> hitEntities = new HashSet<>();

        final BukkitTask[] taskHolder = new BukkitTask[1];

        // Запускаємо повторювану задачу (кожен тік)
        taskHolder[0] = context.scheduleRepeating(() -> {
            // Проходимо кілька мікро-кроків за один тік для плавності і швидкості
            // Якщо step = 1.5, то робимо 1 перевірку за тік. Якщо хочемо швидше, треба цикл.

            if (currentLocation.distanceSquared(start) > maxRangeSquared) {
                taskHolder[0].cancel();
                return;
            }

            // Перевірка колізії з блоками
            if (!currentLocation.getBlock().isPassable()) {
                currentLocation.getWorld().spawnParticle(Particle.EGG_CRACK, currentLocation, 10, 0.5, 0.5, 0.5, currentLocation.getBlock().getBlockData());
                taskHolder[0].cancel();
                return;
            }

            currentLocation.add(direction);
            particleEffect.accept(currentLocation.clone());

            // Хітбокс снаряду (0.8 блок)
            Collection<Entity> nearbyRaw = currentLocation.getWorld().getNearbyEntities(currentLocation, 0.8, 0.8, 0.8);

            for (Entity entity : nearbyRaw) {
                if (entity.getUniqueId().equals(caster.getUniqueId())) continue;
                if (!(entity instanceof LivingEntity)) continue;
                if (hitEntities.contains(entity.getEntityId())) continue;

                LivingEntity target = (LivingEntity) entity;

                // Точна перевірка BoundingBox
                if (target.getBoundingBox().contains(currentLocation.toVector())) {
                    if (onHit.test(target)) {
                        taskHolder[0].cancel();
                        return;
                    }
                    hitEntities.add(target.getEntityId());
                }
            }
        }, 0L, 1L);

        // Страхування від нескінченного польоту (видалення через 5 сек)
        context.scheduleDelayed(() -> {
            if (taskHolder[0] != null && !taskHolder[0].isCancelled()) {
                taskHolder[0].cancel();
            }
        }, 100L);
    }
}