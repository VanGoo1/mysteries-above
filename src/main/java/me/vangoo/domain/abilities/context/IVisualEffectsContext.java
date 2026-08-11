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
     * Різнокольорові вогники, що обертаються навколо сутності й рухаються разом із нею —
     * «на тілі цілі виступають різнобарвні світла». Кольори чергуються по колу, тож одна
     * ціль може нести кілька шляхів одночасно.
     *
     * @param entityId      кого обвиває
     * @param colors        кольори вогників (порожній список — ефекту немає)
     * @param radius        радіус орбіти (блоки)
     * @param durationTicks скільки триває
     */
    void playOrbitingMotes(UUID entityId, java.util.List<Color> colors, double radius,
                           int durationTicks);

    /**
     * Дрібна статична мітка з кольорового пилу: без анімації, без дрейфу, без розльоту —
     * для ненав'язливих маркерів (сліди, підказки), яких може бути багато одночасно.
     * Тримається {@code durationTicks} (перемальовується), потім гасне.
     *
     * @param center        центр мітки
     * @param color         колір пилу
     * @param spread        розкид пилинок довкола центру (блоки)
     * @param size          розмір пилинки
     * @param count         кількість пилинок
     * @param durationTicks скільки мітка тримається; {@code <= 0} — один кадр
     */
    void playDustMark(Location center, Color color, double spread, float size, int count,
                      int durationTicks);

    /**
     * Нерухомий слід по землі від {@code from} до {@code to}: ланцюжок пилинок кольору шляху,
     * покладених на поверхню (пошук ґрунту під кожною точкою), який тримається
     * {@code durationTicks}. Для «куди веде слід» — на відміну від променя, читається як шлях,
     * а не як лазер у повітрі. Довгий маршрут обрізається за кількістю точок.
     *
     * @param from          початок сліду
     * @param to            кінець сліду (той самий світ, інакше ефекту немає)
     * @param color         колір пилу (з PathwayBranding)
     * @param durationTicks скільки слід лежить
     */
    void playGroundTrail(Location from, Location to, Color color, int durationTicks);

    /**
     * {@link #playDustMark}, видима ЛИШЕ одному глядачеві (пакетний партикл, не
     * {@code World.spawnParticle}) — для приватних міток на кшталт слідів
     * Спостережливості, які не мають бачити сторонні.
     *
     * @param viewerId єдиний гравець, який бачить мітку
     */
    void playDustMarkFor(UUID viewerId, Location center, Color color, double spread, float size,
                        int count, int durationTicks);

    /**
     * {@link #playGroundTrail}, видимий ЛИШЕ одному глядачеві — той самий приватний
     * трюк, що й {@link #playDustMarkFor}.
     *
     * @param viewerId єдиний гравець, який бачить слід
     */
    void playGroundTrailFor(UUID viewerId, Location from, Location to, Color color,
                            int durationTicks);

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

    /**
     * Удар блискавки в точку заданого кольору: візуальний розряд (без вогню й шкоди),
     * спалах, висхідний стовп іскор і хмара пилу кольору шляху + грім.
     * {@link #playHolyLightning} — золотий пресет цього ефекту з церемоніальним звуком.
     *
     * @param location точка удару
     * @param color    колір розряду (з PathwayBranding)
     */
    void playLightningBolt(Location location, Color color);

    /**
     * Стіна води, що НАПРЯМЛЕНО котиться вперед від {@code origin} уздовж {@code direction}:
     * вертикальна завіса з пилу кольору шляху рухається щотіка, поки не подолає {@code length}
     * блоків за {@code durationTicks}. На відміну від {@link #playWaveEffect} (кільце, що
     * розходиться на всі боки), це спрямований вал — для хвиль/цунамі, що б'ють уперед.
     * Самодостатній (володіє власним таском); лише малює — шкоду/відкид рахує здібність.
     *
     * @param origin        звідки стартує вал (зазвичай ноги кастера)
     * @param direction     напрям руху валу (горизонтальний)
     * @param length        скільки блоків вал проходить уперед
     * @param width         ширина завіси (упоперек напряму)
     * @param color         колір води (з PathwayBranding)
     * @param durationTicks за скільки тіків вал долає всю довжину
     */
    void playSurgingWave(Location origin, org.bukkit.util.Vector direction,
                         double length, double width, Color color, int durationTicks);

    /**
     * СТОЯЧА вертикальна завіса на місці: площина з пилу кольору шляху, перпендикулярна
     * {@code facing}, що брижить (хвиля біжить упоперек) і тримається {@code durationTicks}.
     * На відміну від {@link #playSurgingWave} (вал, що котиться вперед) — не рухається:
     * для щитів, стін і бар'єрів. Самодостатня (володіє власним таском); лише малює.
     *
     * @param center        центр завіси (низ; висота росте вгору)
     * @param facing        напрям, КУДИ дивиться завіса (площина перпендикулярна йому)
     * @param width         ширина завіси
     * @param height        висота завіси
     * @param color         колір (з PathwayBranding)
     * @param durationTicks скільки завіса стоїть
     */
    void playStandingCurtain(Location center, org.bukkit.util.Vector facing,
                             double width, double height, Color color, int durationTicks);

    /**
     * ВОРОТА: вертикальний прямокутний портал — рамка кольору шляху, темна утроба всередині
     * (дим/чорнило, а не суцільна заливка) і кільця всмоктування, що стягуються до центру.
     * На відміну від {@link #playStandingCurtain} (рухома брижами завіса без нутра) — це
     * непрозорий отвір, крізь який щось тягне; на відміну від {@link #playVortexEffect}
     * (вихор навколо точки) — площинний, як двері, а не об'ємний вир. Самодостатній
     * (володіє власним таском); лише малює — тягу й шкоду рахує здібність.
     *
     * @param center        низ брами (ноги); висота росте вгору, як у {@link #playStandingCurtain}
     * @param facing        напрям, КУДИ дивиться брама (площина перпендикулярна йому)
     * @param width         ширина брами
     * @param height        висота брами
     * @param color         колір рамки й кілець (з PathwayBranding)
     * @param durationTicks скільки брама стоїть
     */
    void playUnderworldGate(Location center, org.bukkit.util.Vector facing,
                            double width, double height, Color color, int durationTicks);

    /**
     * ХВАТ: примарні руки, що піднімаються з-під ніг цілі й стискаються навколо неї, плюс
     * кільце на землі («земля не пускає») і душевні іскри на кінчиках пальців. Свідомо НЕ
     * {@link #playCircleEffect} і не {@link #playVortexEffect}: там кільце й вихор, а тут
     * потрібні саме руки, що тримають — інакше іммобілізація читається як звичайна аура.
     * Самодостатній (володіє власним таском); лише малює — сповільнення накладає здібність.
     *
     * @param target        локація цілі (ноги); ефект статичний, тож для довгого хвату
     *                      викликається повторно за новою локацією
     * @param color         колір рук (з PathwayBranding)
     * @param durationTicks скільки руки тримаються
     */
    void playGraspingHands(Location target, Color color, int durationTicks);

    /**
     * Блоки-примари: ОДИН гравець бачить {@code count} фальшивих блоків {@code material},
     * що з'являються у порожніх клітинках у радіусі {@code radius} довкола {@code center};
     * решта світу нічого не бачить. Через {@code durationTicks} усі клітинки самі
     * повертаються до справжнього стану — ефект володіє власним таском, тож здібності
     * не потрібна сесія. Клітинки, що перетинають хітбокс глядача, ніколи не підмінюються
     * (жодного задушення в ілюзії).
     *
     * @param viewerId      єдиний гравець, який бачить ілюзію
     * @param center        центр ілюзії
     * @param material      матеріал фальшивих блоків
     * @param count         скільки клітинок підмінити
     * @param radius        радіус пошуку порожніх клітинок
     * @param durationTicks скільки ілюзія тримається
     */
    void playPhantomBlocks(UUID viewerId, Location center, org.bukkit.Material material,
                           int count, double radius, int durationTicks);

    /**
     * Куполоподібна оболонка, що РУХАЄТЬСЯ разом із сутністю: сітка з пилу кольору шляху
     * (паралелі купола + кільце по землі), яка повільно обертається, доки не спливе
     * {@code durationTicks}. На відміну від {@link #playSphereEffect} (сфера прив'язана до
     * статичної точки) і {@link #playPersistentHalo} (плаский німб) — для вікон поглинання,
     * щитів і коконів. Самодостатня (володіє власним таском).
     *
     * @param entityId      кого огортає оболонка
     * @param color         колір (з PathwayBranding)
     * @param radius        радіус оболонки
     * @param durationTicks скільки оболонка тримається
     */
    void playWardingShell(UUID entityId, Color color, double radius, int durationTicks);

    /**
     * Силует душі, видимий ЛИШЕ одному глядачеві: висхідний стовпчик {@code SOUL} у людський
     * зріст плюс кільце-німб з пилу кольору шляху над ним. Один кадр — власного таска не має,
     * тож той, хто малює душу довго, перемальовує її своїм тіком.
     *
     * <p>Свідомо не {@link #playPillarEffect}: той малює стовп усьому світу, а душу на місці
     * смерті має бачити тільки медіум.
     *
     * @param viewerId єдиний гравець, який бачить силует
     * @param base     низ силуету (місце смерті)
     * @param color    колір німба (з PathwayBranding)
     */
    void playSoulWisp(UUID viewerId, Location base, Color color);
}
