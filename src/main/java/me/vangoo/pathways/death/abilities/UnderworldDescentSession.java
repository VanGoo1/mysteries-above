package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.context.IEventContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.pathways.common.Spirits;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Сходження Загробного Світу (Death, Посл. 5, режим {@code DESCEND} Дверей) — зона, що
 * «насувається на реальність»: темрява й Тління ворогам, надбавка до удару власної нежиті
 * всередині, повільне лікування власника, поки він у межах. На відміну від
 * {@link FrostShadowSession} (домен ходить за медіумом) зона тут ФІКСОВАНА в точці касту.
 *
 * <p>Підписка на удари слуг власника — під ВЛАСНИМ ключем (не {@code ownerId}), тим самим
 * прийомом, що коса Морозної тіні: чужий {@code unsubscribeAll(ownerId)} інакше зняв би
 * надбавку. Сесія сама гасне за спливом строку; {@link #cancel()} також кличеться при
 * повторному касті DESCEND (заміна старої зони) і з {@code cleanUp()} здібності на вимкнення
 * плагіна.
 */
final class UnderworldDescentSession {

    static final long TICK_PERIOD_TICKS = 20L;

    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;
    /** Ефекти поновлюються з запасом понад період такту, щоб не мигали між тіками. */
    private static final int EFFECT_REFRESH_TICKS = 45;
    private static final int WITHER_AMPLIFIER = 0;
    private static final int DARKNESS_AMPLIFIER = 0;

    private final UUID ownerId;
    private final UUID subscriptionKey = UUID.randomUUID();
    private final Location center;
    private final Vector facing;
    private final double gateWidth;
    private final double gateHeight;
    private final double radius;
    private final double undeadDamageMultiplier;
    private final double healPerSecond;
    private final long expiresAtMillis;
    private final Color color;
    private final IEventContext events;
    private final IVisualEffectsContext effects;
    private final UndeadRetinue retinue;
    private final Map<UUID, UnderworldDescentSession> sessions;

    private BukkitTask task;

    UnderworldDescentSession(UUID ownerId, Location center, Vector facing, double gateWidth,
                             double gateHeight, double radius, double undeadDamageMultiplier,
                             double healPerSecond, int durationSeconds, Color color,
                             IEventContext events, IVisualEffectsContext effects,
                             UndeadRetinue retinue, Map<UUID, UnderworldDescentSession> sessions) {
        this.ownerId = ownerId;
        this.center = center.clone();
        this.facing = facing.clone();
        this.gateWidth = gateWidth;
        this.gateHeight = gateHeight;
        this.radius = radius;
        this.undeadDamageMultiplier = undeadDamageMultiplier;
        this.healPerSecond = healPerSecond;
        this.expiresAtMillis = System.currentTimeMillis() + durationSeconds * 1000L;
        this.color = color;
        this.events = events;
        this.effects = effects;
        this.retinue = retinue;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Вішає надбавку на удари слуг власника всередині зони. Кличеться один раз, при створенні. */
    void empowerUndead() {
        events.subscribeToTemporaryEvent(
                subscriptionKey,
                EntityDamageByEntityEvent.class,
                event -> event.getDamager() instanceof LivingEntity damager
                        && retinue.isOwnedBy(ownerId, damager.getUniqueId())
                        && damager.getLocation().distanceSquared(center) <= radius * radius,
                event -> event.setDamage(event.getDamage() * undeadDamageMultiplier),
                PERMANENT_DURATION
        );
    }

    void tick() {
        if (System.currentTimeMillis() >= expiresAtMillis || center.getWorld() == null) {
            cancel();
            return;
        }

        effects.playUnderworldGate(center, facing, gateWidth, gateHeight, color, (int) TICK_PERIOD_TICKS);
        effects.playSphereEffect(center, radius, Particle.SQUID_INK, (int) TICK_PERIOD_TICKS);
        effects.playCircleEffect(center, radius, Particle.SOUL_FIRE_FLAME, (int) TICK_PERIOD_TICKS);
        effects.playSound(center, Sound.AMBIENT_CAVE, 0.8f, 0.6f);
        effects.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.5f);

        double radiusSquared = radius * radius;

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline() && owner.isValid()
                && owner.getWorld().equals(center.getWorld())
                && owner.getLocation().distanceSquared(center) <= radiusSquared) {
            healOwner(owner);
        }

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity victim) || victim.getUniqueId().equals(ownerId)) continue;
            if (retinue.isOwnedBy(ownerId, victim.getUniqueId()) || Spirits.isSpirit(victim)) continue;
            // getNearbyEntities дає куб; зона — сфера, інакше по кутах вона ширша за задане число.
            if (victim.getLocation().distanceSquared(center) > radiusSquared) continue;

            afflict(victim);
        }
    }

    private void healOwner(Player owner) {
        var maxHealthAttr = owner.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        owner.setHealth(Math.min(maxHealth, owner.getHealth() + healPerSecond));
    }

    private void afflict(LivingEntity victim) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, EFFECT_REFRESH_TICKS,
                DARKNESS_AMPLIFIER, false, false, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, EFFECT_REFRESH_TICKS,
                WITHER_AMPLIFIER, false, true, true));
    }

    /** Зона гасне: підписка знімається, таск зупиняється. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId, this);
        events.unsubscribeAll(subscriptionKey);

        if (center.getWorld() != null) {
            effects.playFadingAura(center, color, EFFECT_REFRESH_TICKS);
            effects.playSound(center, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 0.5f);
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.DARK_PURPLE + "Загробний Світ відступив");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
