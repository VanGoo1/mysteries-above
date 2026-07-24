package me.vangoo.domain.abilities.context;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public interface IVisualEffectsContext {
    void playSound(Location loc, Sound sound, float volume, float pitch);

    void playSoundForPlayer(UUID playerId, Sound sound, float volume, float pitch);

    void spawnParticle(Particle type, Location loc, int count);

    void spawnParticle(Particle type, Location loc, int count,
                       double offsetX, double offsetY, double offsetZ);

    void spawnParticleForPlayer(UUID receiverId, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ);

    /**
     * Create a sphere effect at location
     *
     * @param location      Center of sphere
     * @param radius        Radius of sphere
     * @param particle      Particle type to use
     * @param durationTicks How long effect lasts
     */
    void playSphereEffect(Location location, double radius, Particle particle, int durationTicks);

    /**
     * Create a helix/spiral effect between two points
     *
     * @param start         Start location
     * @param end           End location
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playHelixEffect(Location start, Location end, Particle particle, int durationTicks);

    /**
     * Create a circle effect at location
     *
     * @param location      Center of circle
     * @param radius        Radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playCircleEffect(Location location, double radius, Particle particle, int durationTicks);

    /**
     * Create a line effect between two points
     *
     * @param start    Start location
     * @param end      End location
     * @param particle Particle type
     */
    void playLineEffect(Location start, Location end, Particle particle);

    /**
     * Create a cone effect (useful for directional abilities)
     *
     * @param apex          Tip of cone
     * @param direction     Direction cone points
     * @param angle         Cone opening angle in degrees
     * @param length        Length of cone
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playConeEffect(Location apex, org.bukkit.util.Vector direction, double angle,
                        double length, Particle particle, int durationTicks);

    /**
     * Create a vortex/tornado effect
     *
     * @param location      Center location
     * @param height        Height of vortex
     * @param radius        Base radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playVortexEffect(Location location, double height, double radius,
                          Particle particle, int durationTicks);

    /**
     * Create a wave effect emanating from location
     *
     * @param center        Center point
     * @param radius        Wave radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playWaveEffect(Location center, double radius, Particle particle, int durationTicks);

    /**
     * Create a cube outline effect
     *
     * @param location      Center of cube
     * @param size          Size of cube edges
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playCubeEffect(Location location, double size, Particle particle, int durationTicks);

    /**
     * Create an animated trail effect following an entity
     *
     * @param entityId      Entity to follow
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playTrailEffect(UUID entityId, Particle particle, int durationTicks);

    /**
     * Create a beam effect between two locations (laser-like)
     *
     * @param start         Start location
     * @param end           End location
     * @param particle      Particle type
     * @param width         Beam width
     * @param durationTicks Duration
     */
    void playBeamEffect(Location start, Location end, Particle particle,
                        double width, int durationTicks);

    /**
     * Create an explosion ring effect
     *
     * @param center   Center of explosion
     * @param radius   Ring radius
     * @param particle Particle type
     */
    void playExplosionRingEffect(Location center, double radius, Particle particle, Particle.DustOptions options);

    void playAlertHalo(Location location, Color color);

    /**
     * Тонкий м'який промінь, що ПОСТУПОВО тягнеться від {@code start} до {@code end}
     * (голова променя рухається щотіка, лишаючи короткий шлейф), а коли досягає цілі —
     * викликає {@code onArrival}. Колір бере відтінок шляху (через PathwayBranding);
     * ефект тонкий і напівпрозорий, не «лазер».
     *
     * @param start     звідки починається промінь (зазвичай очі кастера)
     * @param end       ціль променя
     * @param color     колір променя
     * @param onArrival дія в момент влучання (може бути {@code null})
     */
    void playTravelingBeam(Location start, Location end, Color color, Runnable onArrival);

    /**
     * М'який спалах святого світла з подальшим золотим пилом, що повільно дрейфує
     * угору на невелику відстань і поступово згасає — «сяючий пил у сонячному промені».
     * Не вибух, не хаотичний розліт.
     *
     * @param center центр ефекту (точка влучання)
     * @param color  колір пилу
     */
    void playGlowingDust(Location center, Color color);

    /**
     * Разова висхідна спіраль навколо точки: пилинки кольору шляху шикуються у спіраль
     * від {@code base} до {@code base + height} і «повзуть» знизу вгору протягом
     * {@code durationTicks}, після чого ефект сам гасне. Самодостатній (володіє власним
     * таском) — грається один раз, напр. у момент активації здібності.
     *
     * @param base          низ спіралі (зазвичай ноги кастера)
     * @param height        висота стовпа спіралі
     * @param radius        радіус спіралі
     * @param color         колір пилу (з PathwayBranding)
     * @param durationTicks скільки триває анімація
     */
    void playRisingSpiral(Location base, double height, double radius,
                          Color color, int durationTicks);

    /**
     * Разова аура кольору шляху, що огортає тіло цілі та швидко згасає: оболонка з пилу
     * довкола гравця дрібнішає й рідшає протягом {@code durationTicks}. Самодостатня
     * (володіє власним таском) — короткий сплеск у момент, напр., активації здібності.
     *
     * @param base          низ цілі (зазвичай ноги гравця)
     * @param color         колір аури (з PathwayBranding)
     * @param durationTicks скільки триває згасання
     */
    void playFadingAura(Location base, Color color, int durationTicks);

    /**
     * Товстий стовп світла від {@code base} до {@code base + height}: щільне
     * об'ємне ядро (кілька концентричних кілець на кожному рівні висоти, що
     * повільно обертаються) + висхідні іскри вздовж зовнішнього краю. Читається
     * як товстий обертовий стовп, не тонка лінія-промінь.
     *
     * @param base          низ стовпа
     * @param height        висота стовпа
     * @param radius        радіус стовпа
     * @param color         колір (з PathwayBranding)
     * @param durationTicks скільки триває анімація
     */
    void playPillarEffect(Location base, double height, double radius,
                          Color color, int durationTicks);

    /**
     * Персистентний німб над головою сутності: золоте кільце, що плавно
     * обертається й лишається видимим, доки власник не скасує повернутий
     * {@link BukkitTask} (напр. коли аура вимикається). Стежить за позицією
     * сутності щотіка — не прив'язаний до статичної точки.
     *
     * @param entityId сутність, над головою якої тримається німб
     * @param color    колір кільця (з PathwayBranding)
     * @return таск ефекту — власник відповідає за {@code cancel()}
     */
    BukkitTask playPersistentHalo(UUID entityId, Color color);

    /**
     * Золоті «письмена» — дрібні світні гліфи, що повільно кружляють довкола гравця й
     * поступово згасають. Для церемонії засвідчення контракту Sun (не хаотична хмара —
     * упорядковані орбітальні знаки). Самодостатній (володіє власним таском).
     *
     * @param center        центр аури (зазвичай ноги/тулуб гравця)
     * @param color         колір письмен (з PathwayBranding)
     * @param durationTicks тривалість анімації
     */
    void playScriptureAura(Location center, Color color, int durationTicks);

    /**
     * Сонячний стовп, що коротко СПУСКАЄТЬСЯ з неба на точку: голова стовпа падає згори
     * вниз, лишаючи згасаючий слід, і завершується спалахом на землі. Відрізняється від
     * {@link #playPillarEffect} (той росте знизу вгору й тримається). Самодостатній.
     *
     * @param target точка приземлення стовпа
     * @param color  колір (з PathwayBranding)
     */
    void playDescendingSunPillar(Location target, Color color);

    /**
     * Зламаний Сонячний Диск над головою — персистентний тріснутий золотий диск (дуга з
     * розривом, що похитується), видимий іншим гравцям поблизу. Сам скасовується за
     * {@code durationTicks} (тривалість печатки Божественної кари). Стежить за позицією
     * сутності щотіка.
     *
     * @param entityId      над ким тримається диск
     * @param color         колір диска (з PathwayBranding)
     * @param durationTicks скільки диск лишається (= тривалість печатки)
     */
    void playBrokenSunDisc(UUID entityId, Color color, int durationTicks);

    /**
     * Свята блискавка — візуальний удар блискавки (без вогню/шкоди) з золотим спалахом і
     * висхідним стовпом іскор у точці влучання. Ефект-складова Божественної кари.
     *
     * @param location точка удару
     */
    void playHolyLightning(Location location);
}
