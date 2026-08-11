package me.vangoo.infrastructure.compat;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

/**
 * Керування AI моба ванільним Bukkit'ом — там, де раніше стояло Paper-API.
 *
 * <p>Paper дає {@code Bukkit.getMobGoals()} (точкове зняття гоалів) і {@code Mob#getPathfinder()}
 * (навігація з обходом перешкод). На сервері проєкту (Arclight) ні того, ні іншого немає:
 * виклик падав {@code NoSuchMethodError} уже в грі, хоча збірка мовчала. Проєкт компілюється
 * проти Spigot-API саме тому, що таке має ловити компілятор — а всі заміни живуть тут, в
 * одному місці, а не розповзаються по здібностях.
 *
 * <p>Заміни слабші за оригінал, і це чесно зафіксовано в кожному методі. Найважливіше:
 * <b>ходу тепер задає імпульс швидкості, а не маршрут</b>, тож моб іде по прямій і на
 * складному рельєфі застрягає — той, хто його веде, мусить мати власний перескок по відстані.
 */
public final class MobAiCompat {

    /** Один імпульс ходи (блоків за тік) при швидкості 1.0; тертя гасить його за ~5 тіків. */
    private static final double IMPULSE = 0.28;
    /** Ближче за це до цілі поштовх не потрібен — моб і так на місці. */
    private static final double ARRIVAL = 0.4;

    private MobAiCompat() {
    }

    /**
     * «Без волі»: моб перестає сам обирати ворога й мститися за удар.
     *
     * <p>Точково (лише цільові гоали) це вміє тільки Paper, тож AI глушиться цілком
     * ({@code setAware(false)}). Наслідок, який мусить пам'ятати кличучий: рух тепер веде
     * {@link #walkTo}, а на час наказу «атакувати» AI треба ввімкнути назад —
     * {@link #allowVanillaCombat}.
     */
    public static void suppressTargeting(Mob mob) {
        if (mob != null) mob.setAware(false);
    }

    /**
     * Повертає мобу ванільний ближній бій по виставленій ззовні цілі.
     *
     * <p>Платою за глушіння AI є те, що на цей час моб може перевибрати ціль сам — той, хто
     * командує, мусить перевиставляти її щотакту.
     */
    public static void allowVanillaCombat(Mob mob) {
        if (mob != null) mob.setAware(true);
    }

    /** Веде моба до точки поштовхом швидкості (див. застереження в описі класу). */
    public static void walkTo(Mob mob, Location target, double speed) {
        if (mob == null || target == null) return;
        push(mob, target, speed);
    }

    /** Те саме до живої цілі — вона рухається, тож точка береться на момент виклику. */
    public static void walkTo(Mob mob, LivingEntity target, double speed) {
        if (mob == null || target == null) return;
        push(mob, target.getLocation(), speed);
    }

    /** Зупиняє рух: гасить горизонтальну швидкість, вертикаль лишає фізиці. */
    public static void stopMoving(Mob mob) {
        if (mob == null) return;

        Vector velocity = mob.getVelocity();
        mob.setVelocity(new Vector(0.0, velocity.getY(), 0.0));
    }

    /**
     * Повертає моба обличчям до цілі. Ванільного повороту голови в Bukkit немає, тож це
     * телепорт на те саме місце з новим напрямком — позиція не міняється.
     */
    public static void lookAt(Mob mob, LivingEntity target) {
        if (mob == null || target == null) return;

        Location from = mob.getLocation();
        Location to = target.getEyeLocation();
        if (from.getWorld() != to.getWorld()) return;

        Vector direction = to.toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0e-6) return;

        mob.teleport(from.setDirection(direction));
    }

    /** Поштовх у бік цілі: тільки горизонталь, вертикаль лишається за фізикою. */
    private static void push(Mob mob, Location target, double speed) {
        Location from = mob.getLocation();
        if (from.getWorld() != target.getWorld()) return;

        Vector delta = target.toVector().subtract(from.toVector()).setY(0.0);
        if (delta.length() < ARRIVAL) return;

        Vector step = delta.normalize().multiply(IMPULSE * speed);
        mob.setVelocity(new Vector(step.getX(), mob.getVelocity().getY(), step.getZ()));
    }
}
