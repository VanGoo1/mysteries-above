package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 8 шляху Смерті (Могильник).
 *
 * <p>Одне джерело правди для тіру: Спілкування з духами (вербування/прикликання почту й хват
 * духа) та Око Смерті (вузол — один посилений удар). Чиста математика — без Bukkit; ефекти
 * живуть у {@code pathways.death.abilities}. Стати самого духа (HP, урон, швидкість) тут НЕ
 * лежать — вони належать MythicMobs-паку ({@code mythic-pack/Mobs/death.yml}).
 *
 * <p>Посл. 8 — перша для цього тіру, тож усі базові числа задані саме для неї, а
 * {@link #growth(Sequence, ScalingStrategy)} лише піднімає їх для сильніших Послідовностей
 * (на Посл. 8 множник рівно 1.0). Числа Посл. 9 живуть окремо, у {@link CorpseCollectorLore}.
 */
public final class GravediggerLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 8;

    private GravediggerLore() {
    }

    // --- Тіло могильника (PhysicalEnhancement, спільний identity "death_physique") ---
    /** База розрахунку бонусного HP; удвічі більше за Посл. 9 — «stronger and more agile». */
    public static final int PHYSIQUE_HP_BASE = 6;

    // --- Спілкування з духами: ціни ---
    /** Прикликати духа зі Світу Духів (нікого немає поблизу). */
    public static final int SPIRIT_SUMMON_COST = 75;
    /** Домовитись із духом, що вже блукає поруч — дешевше за прикликання. */
    public static final int SPIRIT_RECRUIT_COST = 25;

    private static final int SPIRIT_COOLDOWN_SECONDS = 30;
    private static final int SPIRIT_COOLDOWN_FLOOR_SECONDS = 15;

    // --- Спілкування з духами: почет ---
    private static final int SPIRIT_LIMIT = 2;
    private static final int SPIRIT_LIMIT_CAP = 6;
    private static final double SPIRIT_RECRUIT_RADIUS = 16.0;
    private static final double SPIRIT_RECRUIT_RADIUS_CAP = 48.0;

    /** Радіус кола, яким дух в'ється НАД головою власника — щоб не ловити його удари. */
    public static final double SPIRIT_FOLLOW_DISTANCE = 2.0;
    /** Відстав далі — не біжить, а перескакує (інакше загубився б за стінами й водою). */
    public static final double SPIRIT_TELEPORT_DISTANCE = 32.0;

    // --- Хват духа (реакція почту на удар власника) ---
    /** Скільки триває хват після удару; КОЖЕН новий удар поновлює відлік із нуля. */
    private static final int GRASP_SECONDS = 10;
    /**
     * Довжина «рук» духа по ГОРИЗОНТАЛІ: дух висить над головою жертви, руки тягнуться згори,
     * тож висота у відстань не рахується. Від Послідовності не залежить.
     */
    public static final double GRASP_REACH = 3.0;
    /** Рівень Повільності при хваті (0 = I), тобто Повільність IV. Це сила хвату, не сила духа. */
    public static final int GRASP_SLOWNESS_AMPLIFIER = 3;

    // --- Око Смерті (вузол) ---
    public static final int EYE_COST = 50;
    private static final int EYE_COOLDOWN_SECONDS = 14;
    private static final int EYE_COOLDOWN_FLOOR_SECONDS = 5;
    private static final double EYE_RANGE = 24.0;
    private static final double EYE_RANGE_CAP = 64.0;
    /** Надбавка до урону понад 1.0 по МЕРТВОМУ (нежить, дух, реймпейджер): Посл. 8 = +120%. */
    private static final double EYE_DAMAGE_BONUS = 1.2;
    private static final double EYE_DAMAGE_MULTIPLIER_CAP = 3.5;
    /** Вузол один на кастера — повторний каст переносить його на нову ціль. */
    public static final int MAX_CONCURRENT_NODES = 1;
    /**
     * Технічна стеля життя вузла. Гравцеві він видається безстроковим («доки не витрачений»),
     * але підписка на удар мусить мати TTL: без нього релогін лишав би висіти слухача назавжди.
     */
    public static final int NODE_TTL_SECONDS = 30 * 60;

    /** Кулдаун Спілкування з духами (сек): Посл. 8 = 15, коротшає WEAK, не нижче підлоги. */
    public static int spiritCooldownSeconds(Sequence sequence) {
        double seconds = SPIRIT_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(SPIRIT_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /** Скільки духів тримається водночас: Посл. 8 = 2, росте MODERATE до стелі 6. */
    public static int spiritLimit(Sequence sequence) {
        double limit = SPIRIT_LIMIT * growth(sequence, ScalingStrategy.MODERATE);
        return (int) Math.min(SPIRIT_LIMIT_CAP, Math.round(limit));
    }

    /** Радіус пошуку вільного духа для вербування (блоки): Посл. 8 = 16, росте WEAK до стелі 48. */
    public static double spiritRecruitRadius(Sequence sequence) {
        return Math.min(SPIRIT_RECRUIT_RADIUS_CAP,
                SPIRIT_RECRUIT_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Скільки дух тримає одну ціль (сек): Посл. 8 = 6, росте MODERATE. */
    public static int graspSeconds(Sequence sequence) {
        return (int) Math.round(GRASP_SECONDS * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Кулдаун Ока Смерті (сек): Посл. 8 = 14, коротшає WEAK, не нижче підлоги. */
    public static int eyeCooldownSeconds(Sequence sequence) {
        double seconds = EYE_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(EYE_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /** Дальність погляду, яким ставиться вузол (блоки): Посл. 8 = 24, росте WEAK до стелі 64. */
    public static double eyeRange(Sequence sequence) {
        return Math.min(EYE_RANGE_CAP, EYE_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /**
     * Множник удару у вузол по МЕРТВОМУ (нежить, дух, реймпейджер): Посл. 8 = 2.2,
     * надбавка росте WEAK до стелі 3.5.
     */
    public static double eyeDeadDamageMultiplier(Sequence sequence) {
        double bonus = EYE_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK);
        return Math.min(EYE_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus);
    }

    /**
     * Множник удару по ЖИВОМУ: половина надбавки мертвого (Посл. 8 = 1.6). Виводиться з
     * {@link #eyeDeadDamageMultiplier}, щоб дві цифри не розʼїхались при зміні балансу.
     */
    public static double eyeLivingDamageMultiplier(Sequence sequence) {
        return 1.0 + (eyeDeadDamageMultiplier(sequence) - 1.0) / 2.0;
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 8 (там рівно 1.0). */
    private static double growth(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(sequence.level(), strategy)
                / SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy);
    }
}
