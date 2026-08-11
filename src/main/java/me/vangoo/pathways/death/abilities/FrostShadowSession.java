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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Морозна тінь (Death, Посл. 7) — єдиний дух цього тіру, що живе в часі, тож і єдиний, хто
 * потребує сесії (решта — разові ефекти в {@link SpiritChannelingRunner}).
 *
 * <p>Вікі: «a Spirit that can create a considerable Frozen domain within a 10 to 20 meter range,
 * and can materialize a layer of armor-like Ice and a colossal, sharp, and crystalline Frost
 * scythe». Звідси три частини: домен холоду довкола медіума (Повільність + обмороження + урон),
 * крижаний обладунок на самому медіумі (Опірність + поглинання) і коса — надбавка до його
 * ближнього удару, поки тінь із ним.
 *
 * <p>Домен ходить за медіумом, а не стоїть де покликаний: обладунок і коса дістались йому, тож
 * тінь супроводжує бійця. Почет духів (Посл. 8) домен НЕ морозить — свої духи власному холоду
 * не жертви.
 *
 * <p>Підписка на удар власника йде під ВЛАСНИМ ключем, не {@code casterId}: чужий
 * {@code unsubscribeAll(casterId)} інакше тихо зняв би косу. Сесія гасне сама, побачивши власника
 * офлайн, — деструктивний {@code cleanUp()} на спільному екземплярі здібності зірвав би домени
 * всім медіумам на вихід будь-якого гравця.
 */
final class FrostShadowSession {

    /** Раз на секунду: домен — не політ, частіше тікати нема сенсу. */
    static final long TICK_PERIOD_TICKS = 20L;

    /** Підписка живе весь домен; ліміт тіків тут був би тихою поломкою коси. */
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;
    /** Ефекти поновлюються з запасом понад період такту, щоб не мигали між тіками. */
    private static final int EFFECT_REFRESH_TICKS = 45;
    /** Рівень Повільності всередині домену (0 = I), тобто Повільність II. */
    private static final int SLOWNESS_AMPLIFIER = 1;
    /** Рівень Опірності від крижаного обладунку (0 = I), тобто Опірність II. */
    private static final int RESISTANCE_AMPLIFIER = 1;
    /** Скільки сердець поглинання дає крижаний обладунок; від Послідовності не залежить. */
    private static final int ARMOR_HEARTS = 4;
    /** Розмах кола коси при ударі (градуси) і його довжина. */
    private static final double SCYTHE_CONE_ANGLE = 45.0;
    private static final double SCYTHE_CONE_LENGTH = 3.0;
    private static final int SCYTHE_CONE_TICKS = 10;

    private final UUID ownerId;
    private final UUID subscriptionKey = UUID.randomUUID();
    private final double radius;
    private final double tickDamage;
    private final double scytheMultiplier;
    private final long expiresAtMillis;
    private final Color color;
    private final IEventContext events;
    private final IVisualEffectsContext effects;
    private final Map<UUID, FrostShadowSession> sessions;

    private BukkitTask task;

    FrostShadowSession(UUID ownerId, double radius, double tickDamage, double scytheMultiplier,
                       int durationSeconds, Color color, IEventContext events,
                       IVisualEffectsContext effects, Map<UUID, FrostShadowSession> sessions) {
        this.ownerId = ownerId;
        this.radius = radius;
        this.tickDamage = tickDamage;
        this.scytheMultiplier = scytheMultiplier;
        this.expiresAtMillis = System.currentTimeMillis() + durationSeconds * 1000L;
        this.color = color;
        this.events = events;
        this.effects = effects;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Вішає косу на удари власника. Кличеться один раз, при створенні сесії. */
    void armScythe() {
        events.subscribeToTemporaryEvent(
                subscriptionKey,
                EntityDamageByEntityEvent.class,
                event -> event.getDamager() instanceof Player player
                        && player.getUniqueId().equals(ownerId)
                        && event.getEntity() instanceof LivingEntity,
                this::swingScythe,
                PERMANENT_DURATION
        );
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            cancel();
            return;
        }
        if (System.currentTimeMillis() >= expiresAtMillis) {
            cancel();
            return;
        }

        Location center = owner.getLocation();
        wearIceArmor(owner);
        effects.playCircleEffect(center, radius, Particle.SNOWFLAKE, (int) TICK_PERIOD_TICKS);

        double radiusSquared = radius * radius;
        for (Entity entity : owner.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity victim)
                    || victim.getUniqueId().equals(ownerId)
                    || Spirits.isSpirit(victim)) {
                continue;
            }
            // getNearbyEntities дає куб; домен — коло, інакше по кутах він ширший за вікіне число.
            if (victim.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            freeze(victim);
        }
    }

    /** Тінь розсіюється: домен гасне, коса знімається, таск зупиняється. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId, this);
        events.unsubscribeAll(subscriptionKey);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            effects.playFadingAura(owner.getLocation(), color, EFFECT_REFRESH_TICKS);
            effects.playSound(owner.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
            owner.sendMessage(ChatColor.AQUA + "❄ Морозна тінь розтанула");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** Крижаний обладунок: ефекти самозгасні, тож {@link #cancel()} нічого знімати не мусить. */
    private void wearIceArmor(Player owner) {
        owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, EFFECT_REFRESH_TICKS,
                RESISTANCE_AMPLIFIER, false, false, true));
        // Поглинання дає 4 HP на рівень, а число тут — у серцях (2 HP): 4 серця = рівень II.
        owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, EFFECT_REFRESH_TICKS,
                ARMOR_HEARTS / 2 - 1, false, false, true));
    }

    /** Холод домену: обмороження (візуал і ванільний урон), Повільність і власний тік урону. */
    private void freeze(LivingEntity victim) {
        victim.setFreezeTicks(victim.getMaxFreezeTicks());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_REFRESH_TICKS,
                SLOWNESS_AMPLIFIER, false, true, true));
        victim.damage(tickDamage);
    }

    /** Коса: удар власника всередині домену йде з надбавкою і лишає по собі морозний віяр. */
    private void swingScythe(EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() * scytheMultiplier);

        Player owner = (Player) event.getDamager();
        effects.playConeEffect(owner.getEyeLocation(), owner.getLocation().getDirection(),
                SCYTHE_CONE_ANGLE, SCYTHE_CONE_LENGTH, Particle.SNOWFLAKE, SCYTHE_CONE_TICKS);
        effects.playSound(event.getEntity().getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 0.7f);
    }
}
