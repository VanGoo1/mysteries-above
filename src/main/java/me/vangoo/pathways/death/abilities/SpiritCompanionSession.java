package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.context.IEventContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.domain.valueobjects.GravediggerLore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Жива сесія почту духів одного могильника (той самий патерн, що {@code JurisdictionSession}):
 * тікає власним Bukkit-таском, водить духів за власником і виконує хват.
 *
 * <p>Дух сам НЕ б'ється: щотакту йому скидається ціль, а при зарахуванні до почту обнуляється
 * атрибут атаки. Єдина «команда» — хват: коли власник когось ударив, вільний дух іде до цієї
 * цілі й кружляє НАД її головою, тримаючи Повільністю. Кожен наступний удар власника поновлює
 * відлік, тож у затяжному бою хват триває, доки духа не вб'ють.
 *
 * <p>Орбіта завжди над маківкою якоря — і біля власника, і над жертвою: на висоті удару дух
 * ловив би випадкові махи власника й гинув від своєї ж сторони.
 *
 * <p>Рухом духа керує сесія, а не ванільний AI: у весксу власні гоали
 * ({@code VexRandomMoveGoal}) щотакту перебивають будь-який {@code moveTo}, тож при зарахуванні
 * гоали знімаються, гравітація вимикається, а сесія веде духа швидкістю по орбіті навколо
 * якоря (власника або жертви). Орбіта — і щоб дух виглядав живим, і щоб у нього не було легко
 * влучити.
 *
 * <p>Підписка на удар власника живе без TTL і знімається у {@link #cancel()} — так само, як
 * {@code GloomyPresence} знімає свою в {@code onDeactivate}. Ключ підписки ВЛАСНИЙ, а не
 * {@code casterId}: чужий {@code unsubscribeAll(casterId)} інакше тихо вбив би хват.
 */
final class SpiritCompanionSession {

    /** Керування польотом — не рідше: на довшому періоді орбіта розсипається на ривки. */
    static final long TICK_PERIOD_TICKS = 2L;

    /** Підписка живе весь час існування почту; ліміт тіків тут був би тихою поломкою хвату. */
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;
    /** Сповільнення поновлюється щотакту з запасом, щоб не мигало між тіками сесії. */
    private static final int SLOWNESS_REFRESH_TICKS = 30;
    /** Малюємо руки раз на секунду, а не щотакту — інакше партиклів більше, ніж потрібно. */
    private static final int HANDS_EVERY = 10;
    /** Далі за це від цілі дух не летить, а перескакує (стіни й вода інакше його губили). */
    private static final double GRASP_TELEPORT_DISTANCE = 24.0;
    /** Крок кута за такт: коло навколо власника ≈ 8 с, навколо жертви ≈ 2.5 с. */
    private static final double FOLLOW_ORBIT_STEP = 2 * Math.PI / 80;
    private static final double GRASP_ORBIT_STEP = 2 * Math.PI / 25;
    /** Стеля швидкості духа (блоків за тік): вище — телепорт замість польоту. */
    private static final double MAX_SPEED = 0.9;
    /** Наскільки дух висить над маківкою: нижче — і власник збиває його своїми ж ударами. */
    private static final double OVERHEAD_CLEARANCE = 1.2;
    /** Радіус кола над головою жертви; менший за {@link GravediggerLore#GRASP_REACH}. */
    private static final double GRASP_ORBIT_RADIUS = 1.4;
    /** Розмах похитування по висоті — щоб дух не висів нерухомою мішенню. */
    private static final double BOB_AMPLITUDE = 0.35;

    private record GraspOrder(UUID targetId, long untilMillis) {}

    private final UUID ownerId;
    private final UUID subscriptionKey = UUID.randomUUID();
    private final int graspSeconds;
    private final Color color;
    private final IEventContext events;
    private final IVisualEffectsContext effects;
    private final Map<UUID, SpiritCompanionSession> sessions;

    /**
     * Почет тримається ЖИВИМИ посиланнями, а не UUID: при переході між світами сервер пересаджує
     * сутність у новий екземпляр (Bukkit-обгортка та сама, а от UUID може змінитись), і пошук за
     * старим id повертав би {@code null} — дух «зникав» разом із сесією.
     */
    private final List<Mob> spirits = new ArrayList<>();
    private final Map<Mob, GraspOrder> grasps = new IdentityHashMap<>();

    private BukkitTask task;
    private int runs;

    SpiritCompanionSession(UUID ownerId, int graspSeconds, Color color,
                           IEventContext events, IVisualEffectsContext effects,
                           Map<UUID, SpiritCompanionSession> sessions) {
        this.ownerId = ownerId;
        this.graspSeconds = graspSeconds;
        this.color = color;
        this.events = events;
        this.effects = effects;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Підписує реакцію почту на удар власника. Кличеться один раз, при створенні сесії. */
    void armGraspTrigger() {
        events.subscribeToTemporaryEvent(
                subscriptionKey,
                EntityDamageByEntityEvent.class,
                event -> event.getDamager() instanceof Player player
                        && player.getUniqueId().equals(ownerId)
                        && event.getEntity() instanceof LivingEntity
                        && !ownerId.equals(event.getEntity().getUniqueId())
                        && !owns(event.getEntity().getUniqueId()),
                event -> orderGrasp(event.getEntity().getUniqueId()),
                PERMANENT_DURATION
        );
    }

    /** Бере духа під команду: більше не б'ється, не блукає сам і не зникає, поки власник онлайн. */
    void enroll(UUID spiritId) {
        if (!(Bukkit.getEntity(spiritId) instanceof Mob spirit)) return;

        spirits.add(spirit);
        spirit.setTarget(null);
        spirit.setRemoveWhenFarAway(false);

        // Ванільні гоали весксу щотакту перебивали б наше ведення; без них рух — лише наш.
        Bukkit.getMobGoals().removeAllGoals(spirit);
        spirit.setGravity(false);

        // Почет тримає, а не вбиває: навіть якщо AI встигне вдарити між тіками — це 0 шкоди.
        AttributeInstance attack = spirit.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(0.0);

        spirit.getWorld().spawnParticle(Particle.SCULK_SOUL, spirit.getEyeLocation(), 12, 0.3, 0.4, 0.3);
    }

    boolean owns(UUID entityId) {
        return spirits.stream().anyMatch(spirit -> spirit.getUniqueId().equals(entityId));
    }

    int size() {
        pruneLostSpirits();
        return spirits.size();
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            cancel();
            return;
        }

        pruneLostSpirits();
        if (spirits.isEmpty()) {
            cancel();
            return;
        }

        runs++;
        long now = System.currentTimeMillis();
        for (Mob spirit : List.copyOf(spirits)) {
            // Почет не воює: цілі скидаються щотакту, рух іде лише від сесії.
            spirit.setTarget(null);

            LivingEntity victim = activeVictim(spirit, now);
            if (victim != null) {
                hold(spirit, victim);
            } else {
                follow(spirit, owner);
            }
        }
    }

    /** Відпускає весь почет: духи розсіюються, підписка знімається, таск гасне. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId, this);
        events.unsubscribeAll(subscriptionKey);

        for (Mob spirit : List.copyOf(spirits)) {
            spirit.getWorld().spawnParticle(Particle.SCULK_SOUL, spirit.getLocation(), 16, 0.3, 0.5, 0.3);
            spirit.getWorld().playSound(spirit.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 0.7f);
            spirit.remove();
        }
        spirits.clear();
        grasps.clear();

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.GRAY + "✦ Духи розсіялись");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Кожен удар власника поновлює хват: якщо цю ціль уже хтось тримає — просто продовжуємо
     * його відлік, і в затяжному бою дух не відпускає жертву, поки його не вб'ють. Інакше наказ
     * дістається першому вільному духові; зайнятих іншою ціллю не перебиваємо.
     */
    private void orderGrasp(UUID victimId) {
        long now = System.currentTimeMillis();
        long until = now + graspSeconds * 1000L;

        for (Mob spirit : spirits) {
            GraspOrder current = grasps.get(spirit);
            if (current != null && current.targetId().equals(victimId)) {
                grasps.put(spirit, new GraspOrder(victimId, until));
                return;
            }
        }
        for (Mob spirit : spirits) {
            GraspOrder current = grasps.get(spirit);
            if (current != null && current.untilMillis() > now) continue;

            grasps.put(spirit, new GraspOrder(victimId, until));
            spirit.getWorld().playSound(spirit.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.7f, 0.6f);
            return;
        }
    }

    /** Ціль поточного хвату або {@code null}, якщо наказ сплився, а жертва зникла чи померла. */
    private LivingEntity activeVictim(Mob spirit, long now) {
        GraspOrder order = grasps.get(spirit);
        if (order == null) return null;

        if (order.untilMillis() <= now
                || !(Bukkit.getEntity(order.targetId()) instanceof LivingEntity victim)
                || victim.isDead()) {
            grasps.remove(spirit);
            return null;
        }
        return victim;
    }

    /** Хват: дух швидко кружляє НАД головою жертви (важко влучити) і тримає її згори. */
    private void hold(Mob spirit, LivingEntity victim) {
        Location victimLoc = victim.getLocation();
        if (!withinLeash(spirit, victimLoc, GRASP_TELEPORT_DISTANCE)) return;

        steer(spirit, orbitPoint(victimLoc, GRASP_ORBIT_RADIUS, overhead(victim),
                spirit, GRASP_ORBIT_STEP));

        // Руки тягнуться згори вниз, тож важить лише горизонтальна відстань — не висота.
        if (horizontalDistance(spirit.getLocation(), victimLoc) > GravediggerLore.GRASP_REACH) return;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOWNESS_REFRESH_TICKS,
                GravediggerLore.GRASP_SLOWNESS_AMPLIFIER, false, true, true));
        if (runs % HANDS_EVERY == 0) {
            effects.playGraspingHands(victimLoc, color, (int) (TICK_PERIOD_TICKS * HANDS_EVERY));
        }
    }

    /** Спокій: дух повільно кружляє над головою власника — так його не збити випадковим ударом. */
    private void follow(Mob spirit, Player owner) {
        Location ownerLoc = owner.getLocation();
        if (!withinLeash(spirit, ownerLoc, GravediggerLore.SPIRIT_TELEPORT_DISTANCE)) return;

        steer(spirit, orbitPoint(ownerLoc, GravediggerLore.SPIRIT_FOLLOW_DISTANCE, overhead(owner),
                spirit, FOLLOW_ORBIT_STEP));
    }

    /** Висота орбіти над ногами істоти: її власний зріст плюс запас над маківкою. */
    private static double overhead(LivingEntity entity) {
        return entity.getHeight() + OVERHEAD_CLEARANCE;
    }

    private static double horizontalDistance(Location first, Location second) {
        return Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
    }

    /** Точка орбіти на цей такт; фаза від духа — щоб кілька духів не злипались в одну крапку. */
    private Location orbitPoint(Location center, double radius, double height,
                                Mob spirit, double step) {
        double angle = runs * step + (System.identityHashCode(spirit) & 0xFF) / 255.0 * 2 * Math.PI;
        return center.clone().add(
                Math.cos(angle) * radius,
                height + Math.sin(angle * 2) * BOB_AMPLITUDE,
                Math.sin(angle) * radius);
    }

    /** Веде духа до точки власною швидкістю: гоали зняті, тож рух цілком за нами. */
    private void steer(Mob spirit, Location point) {
        Vector delta = point.toVector().subtract(spirit.getLocation().toVector());
        double distance = delta.length();
        if (distance < 0.25) {
            spirit.setVelocity(spirit.getVelocity().multiply(0.4));
            return;
        }
        double speed = Math.min(MAX_SPEED, 0.25 + distance * 0.25);
        spirit.setVelocity(delta.multiply(speed / distance));
    }

    /** Відстав чи опинився в іншому світі — перескок; інакше стіни й вода губили б духа. */
    private boolean withinLeash(Mob spirit, Location anchor, double leash) {
        Location spiritLoc = spirit.getLocation();
        if (spiritLoc.getWorld() != anchor.getWorld() || spiritLoc.distance(anchor) > leash) {
            spirit.teleport(anchor);
            return false;
        }
        return true;
    }

    /**
     * Прибирає з почту духів, яких уже немає у світі (убили, знищив /kill). Перехід між світами
     * сюди НЕ потрапляє: обгортка сутності переживає його, а сам перескок робить {@link #withinLeash}.
     */
    private void pruneLostSpirits() {
        List<Mob> lost = new ArrayList<>();
        for (Mob spirit : spirits) {
            if (spirit.isDead() || !spirit.isValid()) {
                lost.add(spirit);
            }
        }
        for (Mob spirit : lost) {
            spirits.remove(spirit);
            grasps.remove(spirit);
        }
    }
}
