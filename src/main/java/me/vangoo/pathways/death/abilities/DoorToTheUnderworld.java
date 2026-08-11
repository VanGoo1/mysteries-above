package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.vangoo.pathways.common.Spirits;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 5: Двері в Загробний Світ (A1–A8). Один портал, чотири режими: {@code DRAG}
 * (тягне й страчує ослаблених коло брами), {@code PURGE} (чистить хмари, шкідливі ефекти
 * й вогонь коло брами), {@code BIND} (скуті руки тримають ворогів коло брами нерухомо),
 * {@code DESCEND} (фіксована зона: темрява й Тління ворогам, надбавка власній нежиті,
 * лікування власника — {@link UnderworldDescentSession}). Жоден режим не бере ціль-сутність
 * (design decision, 2026-08-05): гравець ставить браму в точку, куди дивиться (в межах
 * {@link GatekeeperLore#doorPlacementRange}), і вона діє {@link #GATE_DURATION_SECONDS} —
 * а не б'є миттєво по заздалегідь наведеній цілі.
 *
 * <p>Режим перемикається shift-ПКМ, а не менюшкою чи колесом миші (design decision
 * #7-8, редаговано 2026-08-05): той самий трюк, що {@link InternalUnderworld}
 * ({@code caster.isSneaking()} усередині {@code performExecution} розрізняє «змінити
 * режим» від «кинути двері»), тож перемикання нічого не коштує й не займає кулдаун
 * ({@code AbilityResult.deferred()}), а звичайний каст (без shift) відкриває поточний
 * режим.
 *
 * <p>Ціна й кулдаун ОДНІ на всю здібність незалежно від режиму: {@link #getSpiritualityCost()}/
 * {@link #getCooldown(Sequence)} не бачать ні кастера, ні обраного режиму, тож
 * {@code Beyonder.useAbility}/{@code CooldownManager} фізично не годні дати чотирьом режимам
 * чотири різні числа без ручного дублювання списання ресурсів. Той самий компроміс уже
 * прийнятий {@code Verdict} (Justiciar, Посл. 6) для своїх п'яти режимів. Числа беруть DRAG —
 * найважчий і єдиний поки реалізований режим.
 */
public class DoorToTheUnderworld extends ActiveAbility {

    public enum Mode {
        DRAG("Волочіння"), PURGE("Очищення"), BIND("Скута"), DESCEND("Сходження");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final double GATE_WIDTH = 2.2;
    private static final double GATE_HEIGHT = 3.0;
    /** Скільки брама стоїть і діє (DRAG/PURGE/BIND) — не залежить від Послідовності. */
    private static final int GATE_DURATION_SECONDS = 30;
    /** У цьому радіусі від брами DRAG вважає ціль «проковтнутою» — б'є шкодою/Тлінням/стратою. */
    private static final double DRAG_SWALLOW_RADIUS = 1.5;

    /** Кастер → поточний обраний режим (за замовчуванням DRAG). */
    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();
    /** Кастер → активна брама DRAG/PURGE/BIND. Повторний каст замінює й скасовує стару. */
    private final Map<UUID, DoorGateSession> gateSessions = new ConcurrentHashMap<>();
    /** Кастер → активна зона DESCEND. Повторний каст замінює й скасовує стару. */
    private final Map<UUID, UnderworldDescentSession> descentSessions = new ConcurrentHashMap<>();

    /** Почет Посл. 6+: спільний реєстр, тому приходить конструктором, як у SpiritChanneling. */
    private final UndeadRetinue retinue;

    public DoorToTheUnderworld(UndeadRetinue retinue) {
        this.retinue = retinue;
    }

    @Override
    public String getName() {
        return "Двері в Загробний Світ";
    }

    @Override
    public String getDescription(Sequence sequence) {
        double placementRange = GatekeeperLore.doorPlacementRange(sequence);
        int damage = GatekeeperLore.dragDamage(sequence);
        double dragAoe = GatekeeperLore.dragGateAoeRadius(sequence);
        double purgeRadius = GatekeeperLore.purgeRadius(sequence);
        double bindRadius = GatekeeperLore.bindRadius(sequence);
        double descendRadius = GatekeeperLore.descendRadius(sequence);
        int descendSeconds = GatekeeperLore.descendDurationSeconds(sequence);
        return String.format(
                "§fВи ставите двері до Загробного Світу в точці, куди дивитесь (до %.0f " +
                        "блоків).\n" +
                        "Shift + ПКМ — зміна режиму, звичайне ПКМ — відкрити двері:\n",
                placementRange,
                GATE_DURATION_SECONDS, dragAoe, damage, GatekeeperLore.DRAG_EXECUTE_HP_FRACTION * 100,
                GATE_DURATION_SECONDS, purgeRadius,
                GATE_DURATION_SECONDS, bindRadius,
                descendRadius, descendSeconds, GatekeeperLore.DESCEND_UNDEAD_DAMAGE_BONUS * 100);
    }

    @Override
    public int getSpiritualityCost() {
        return GatekeeperLore.DOOR_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return GatekeeperLore.doorCooldownSeconds(sequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }
        UUID casterId = context.getCasterId();

        if (caster.isSneaking()) {
            return switchMode(context, casterId);
        }

        Mode mode = modes.getOrDefault(casterId, Mode.DRAG);
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Location gateBase = resolveGateBase(caster, GatekeeperLore.doorPlacementRange(sequence));

        return switch (mode) {
            case DRAG -> openDrag(context, caster, gateBase, sequence);
            case PURGE -> openPurge(context, caster, gateBase, sequence);
            case BIND -> openBind(context, caster, gateBase, sequence);
            case DESCEND -> openDescend(context, caster, gateBase, sequence);
        };
    }

    private AbilityResult switchMode(IAbilityContext context, UUID casterId) {
        Mode next = modes.getOrDefault(casterId, Mode.DRAG).next();
        modes.put(casterId, next);

        context.messaging().sendMessageToActionBar(casterId, Component.text(
                "Двері: режим " + next.displayName, NamedTextColor.DARK_PURPLE));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
        return AbilityResult.deferred();
    }

    /**
     * Точка на поверхні, куди дивиться гравець (у межах {@code range}); нема поверхні —
     * точка в повітрі. Точний рейкаст (не {@code getTargetBlockExact} + координата блоку) —
     * бо координата блоку завжди його НИЖНІЙ кут: додавання 0 по Y ставило браму на блок
     * нижче за поверхню, під землю.
     */
    private Location resolveGateBase(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = caster.getWorld().rayTraceBlocks(
                eye, direction, range, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(caster.getWorld());
        }
        return eye.clone().add(direction.multiply(range));
    }

    private AbilityResult openDrag(IAbilityContext context, Player caster, Location gateBase, Sequence sequence) {
        UUID casterId = caster.getUniqueId();
        Vector facing = caster.getLocation().getDirection().setY(0).normalize();
        Location gateCenter = gateBase.clone().add(0, GATE_HEIGHT / 2.0, 0);
        Color color = PathwayBranding.liquidOf("Death");
        double aoeRadius = GatekeeperLore.dragGateAoeRadius(sequence);
        int damage = GatekeeperLore.dragDamage(sequence);
        IVisualEffectsContext effects = context.effects();

        context.effects().playSound(gateBase, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.4f);
        context.effects().playSound(gateBase, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.3f);

        startGateSession(context, casterId, gateBase, facing, color,
                () -> tickDrag(casterId, gateCenter, aoeRadius, damage, effects));

        return AbilityResult.success();
    }

    /** Щотакту: у зовнішньому радіусі тягне й скручує, впритул до брами — шкода/Тління/страта. */
    private void tickDrag(UUID casterId, Location gateCenter, double aoeRadius, int damage,
                           IVisualEffectsContext effects) {
        if (gateCenter.getWorld() == null) return;

        double radiusSquared = aoeRadius * aoeRadius;
        double swallowSquared = DRAG_SWALLOW_RADIUS * DRAG_SWALLOW_RADIUS;

        for (Entity entity : gateCenter.getWorld().getNearbyEntities(gateCenter, aoeRadius, aoeRadius, aoeRadius)) {
            if (!(entity instanceof LivingEntity victim) || victim.getUniqueId().equals(casterId)) continue;
            Location victimLocation = victim.getLocation();
            double distanceSquared = victimLocation.distanceSquared(gateCenter);
            if (distanceSquared > radiusSquared) continue;

            // Горизонтальна відстань, не 3D: gateCenter піднятий на GATE_HEIGHT/2 над землею,
            // тож наземна ціль ніколи не потрапляла в 1.5-блоковий "впритул" по повній 3D-відстані.
            double dx = victimLocation.getX() - gateCenter.getX();
            double dz = victimLocation.getZ() - gateCenter.getZ();
            double horizontalSquared = dx * dx + dz * dz;

            if (horizontalSquared <= swallowSquared) {
                victim.setHealth(Math.max(0.0, victim.getHealth() - damage));
                victim.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER, GatekeeperLore.DRAG_WITHER_SECONDS * 20, 0));

                AttributeInstance maxHealthAttr = victim.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
                if (victim.getHealth() > 0 && victim.getHealth() / maxHealth < GatekeeperLore.DRAG_EXECUTE_HP_FRACTION) {
                    victim.setHealth(0.0);
                    effects.playSound(victim.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
                }
                continue;
            }

            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, GatekeeperLore.DRAG_SLOWNESS_SECONDS * 20, 2));
            pullToward(victim, gateCenter, 0.4);
        }
    }

    /** Тягне ціль до точки; вертикальна складова завжди трохи додатна, щоб не вгрузала в землю. */
    private void pullToward(LivingEntity entity, Location target, double strength) {
        Vector pull = target.toVector().subtract(entity.getLocation().toVector());
        if (pull.lengthSquared() < 1e-4) return;
        pull.normalize().multiply(strength);
        pull.setY(Math.max(0.15, pull.getY()));
        entity.setVelocity(pull);
    }

    /** A5/A6: брама коло себе прибирає шкідливі хмари й лікує союзників — без цілі. */
    private AbilityResult openPurge(IAbilityContext context, Player caster, Location gateBase, Sequence sequence) {
        UUID casterId = caster.getUniqueId();
        Vector facing = caster.getLocation().getDirection().setY(0).normalize();
        Location gateCenter = gateBase.clone().add(0, GATE_HEIGHT / 2.0, 0);
        Color color = PathwayBranding.liquidOf("Death");
        double radius = GatekeeperLore.purgeRadius(sequence);
        IVisualEffectsContext effects = context.effects();

        context.effects().playSound(gateBase, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.4f);
        context.effects().playSound(gateBase, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);

        startGateSession(context, casterId, gateBase, facing, color,
                () -> tickPurge(gateCenter, radius, effects));

        return AbilityResult.success();
    }

    private void tickPurge(Location gateCenter, double radius, IVisualEffectsContext effects) {
        if (gateCenter.getWorld() == null) return;

        for (Entity entity : gateCenter.getWorld().getNearbyEntities(gateCenter, radius, radius, radius)) {
            if (entity instanceof AreaEffectCloud cloud) {
                cloud.remove();
            } else if (entity instanceof Player ally) {
                cleanseAlly(ally);
            }
        }

        effects.playWaveEffect(gateCenter, radius, Particle.CLOUD, (int) DoorGateSession.TICK_PERIOD_TICKS);
        effects.playSound(gateCenter, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
    }

    private static void cleanseAlly(LivingEntity ally) {
        ally.setFireTicks(0);
        ally.removePotionEffect(PotionEffectType.POISON);
        ally.removePotionEffect(PotionEffectType.WITHER);
        ally.removePotionEffect(PotionEffectType.NAUSEA);
    }

    /** A7: скуті руки з-під землі тримають ворогів коло брами — власні слуги/дух не рахуються. */
    private AbilityResult openBind(IAbilityContext context, Player caster, Location gateBase, Sequence sequence) {
        UUID casterId = caster.getUniqueId();
        Vector facing = caster.getLocation().getDirection().setY(0).normalize();
        Location gateCenter = gateBase.clone().add(0, GATE_HEIGHT / 2.0, 0);
        Color color = PathwayBranding.liquidOf("Death");
        double radius = GatekeeperLore.bindRadius(sequence);
        IVisualEffectsContext effects = context.effects();

        context.effects().playSound(gateBase, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.5f);

        startGateSession(context, casterId, gateBase, facing, color,
                () -> tickBind(casterId, gateCenter, radius, color, effects));

        return AbilityResult.success();
    }

    private void tickBind(UUID casterId, Location gateCenter, double radius, Color color,
                           IVisualEffectsContext effects) {
        if (gateCenter.getWorld() == null) return;

        int durationTicks = GatekeeperLore.BIND_DURATION_SECONDS * 20;
        for (Entity entity : gateCenter.getWorld().getNearbyEntities(gateCenter, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.getUniqueId().equals(casterId)) continue;
            if (UndeadRetinue.isServant(target) || Spirits.isSpirit(target)) continue;

            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, durationTicks, GatekeeperLore.BIND_SLOWNESS_AMPLIFIER));
            effects.playGraspingHands(target.getLocation(), color, durationTicks);
        }

        effects.playCircleEffect(gateCenter, radius, Particle.SOUL, (int) DoorGateSession.TICK_PERIOD_TICKS);
    }

    /** Спільний запуск DRAG/PURGE/BIND: заміщає попередню браму кастера й веде тік своїм таском. */
    private void startGateSession(IAbilityContext context, UUID casterId, Location gateBase, Vector facing,
                                   Color color, Runnable tickEffect) {
        DoorGateSession previous = gateSessions.remove(casterId);
        if (previous != null) {
            previous.cancel();
        }

        DoorGateSession session = new DoorGateSession(casterId, gateBase, facing, GATE_WIDTH, GATE_HEIGHT,
                color, GATE_DURATION_SECONDS, context.effects(), tickEffect, gateSessions);
        gateSessions.put(casterId, session);
        var task = context.scheduling().scheduleRepeating(
                session::tick, 0L, DoorGateSession.TICK_PERIOD_TICKS);
        session.bindTask(task);
    }

    /** A8: фіксована зона Загробного Світу в точці погляду — {@link UnderworldDescentSession} веде решту. */
    private AbilityResult openDescend(IAbilityContext context, Player caster, Location gateBase, Sequence sequence) {
        UUID casterId = context.getCasterId();
        Vector facing = caster.getLocation().getDirection().setY(0).normalize();
        double radius = GatekeeperLore.descendRadius(sequence);
        int durationSeconds = GatekeeperLore.descendDurationSeconds(sequence);
        Color color = PathwayBranding.liquidOf("Death");

        UnderworldDescentSession previous = descentSessions.remove(casterId);
        if (previous != null) {
            previous.cancel();
        }

        UnderworldDescentSession session = new UnderworldDescentSession(
                casterId, gateBase, facing, GATE_WIDTH, GATE_HEIGHT, radius,
                1.0 + GatekeeperLore.DESCEND_UNDEAD_DAMAGE_BONUS,
                GatekeeperLore.DESCEND_OWNER_HEAL_PER_SECOND, durationSeconds, color,
                context.events(), context.effects(), retinue, descentSessions);
        descentSessions.put(casterId, session);
        session.empowerUndead();
        var task = context.scheduling().scheduleRepeating(
                session::tick, 0L, UnderworldDescentSession.TICK_PERIOD_TICKS);
        session.bindTask(task);

        context.effects().playSound(gateBase, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.3f);
        context.effects().playSound(gateBase, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.3f);

        return AbilityResult.success();
    }

    @Override
    public void cleanUp() {
        modes.clear();
        // ConcurrentHashMap дозволяє видалення під час ітерації (weakly consistent) — cancel()
        // сам прибирає себе з мапи, тож друге прибирання нижче — просто підстраховка.
        gateSessions.values().forEach(DoorGateSession::cancel);
        gateSessions.clear();
        descentSessions.values().forEach(UnderworldDescentSession::cancel);
        descentSessions.clear();
    }
}
