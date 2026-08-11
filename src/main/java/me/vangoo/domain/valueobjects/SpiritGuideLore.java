package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 6 шляху Смерті (Провідник духів).
 *
 * <p>Тут лежить лише те, що або залежить від Послідовності, або спільне для кількох файлів
 * (ціна бачить і здібність, і меню; строк трупа — і реєстр, і Воскресіння). Одноразові
 * сталі без скейлу живуть у своїх класах — див. пам'ять «Lore VO only for scaled or shared».
 *
 * <p>Посл. 6 — перша для цього тіру, тож усі базові числа задані саме для неї, а
 * {@link #growth(Sequence, ScalingStrategy)} лише піднімає їх для сильніших Послідовностей
 * (на Посл. 6 множник рівно 1.0). Числа слабших тірів — у {@link SpiritMediumLore},
 * {@link GravediggerLore}, {@link CorpseCollectorLore}.
 */
public final class SpiritGuideLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 6;

    private SpiritGuideLore() {
    }

    // --- Тіло провідника (PhysicalEnhancement, спільний identity "death_physique") ---
    public static final int PHYSIQUE_HP_BASE = 8;

    // --- Труп на місці смерті (спільне: реєстр LingeringSouls + Воскресіння) ---
    /** Скільки труп лежить на місці смерті (сек). Душа висить 30 хв, тіло псується швидше. */
    public static final int CORPSE_TTL_SECONDS = 5 * 60;

    // --- Мова мертвих (виривання душі) ---
    public static final int LANGUAGE_COST = 100;
    private static final int LANGUAGE_COOLDOWN_SECONDS = 20;
    private static final int LANGUAGE_COOLDOWN_FLOOR_SECONDS = 5;
    private static final int LANGUAGE_DURATION_SECONDS = 6;
    private static final double LANGUAGE_RANGE = 20.0;
    private static final double LANGUAGE_RANGE_CAP = 32.0;

    // --- Воскресіння (труп → слуга) ---
    public static final int RESURRECTION_COST = 150;
    private static final int RESURRECTION_COOLDOWN_SECONDS = 80;
    private static final int RESURRECTION_COOLDOWN_FLOOR_SECONDS = 20;

    // --- Почет ---
    /**
     * Стеля почту. Свідоме відхилення від вікіних «тисяч» — стільки сутностей уб'є сервер.
     * Спільна для всіх трьох входів (Воскресіння / Підкорення / прикликання істот).
     */
    public static final int RETINUE_CAP = 10;

    // --- Підкорення нежиті (площа) ---
    public static final int SUBJUGATION_COST = 150;
    private static final int SUBJUGATION_COOLDOWN_SECONDS = 45;
    private static final int SUBJUGATION_COOLDOWN_FLOOR_SECONDS = 20;
    private static final double SUBJUGATION_RADIUS = 12.0;
    private static final double SUBJUGATION_RADIUS_CAP = 24.0;

    // --- Домовленість з духами Світу Духів: знайдених у Пеклі, ціна за приєднання до почту
    // (ціни фіксовані: сила істоти й є її ростом) ---
    public static final int LIVING_SHADOW_COST = 50;
    public static final int SHADOW_PYTHON_COST = 60;
    public static final int DEATH_KNIGHT_COST = 70;
    public static final int LAKE_GODDESS_COST = 80;
    private static final int PACT_COOLDOWN_SECONDS = 30;
    private static final int PACT_COOLDOWN_FLOOR_SECONDS = 10;

    // --- Обмін духом (щит від душевних ефектів ціною слуги) ---
    public static final int SPIRIT_SWAP_COST = 110;
    /** Скільки слуг з'їдає обмін. */
    public static final int SPIRIT_SWAP_SERVANT_COST = 1;
    private static final int SPIRIT_SWAP_WARD_SECONDS = 30;
    private static final int SPIRIT_SWAP_COOLDOWN_SECONDS = 90;
    private static final int SPIRIT_SWAP_COOLDOWN_FLOOR_SECONDS = 40;

    // --- Зліт (нежить підкидає провідника вгору) ---
    public static final int SOAR_COST = 15;
    /** Антиспам, не сила — тому стала. */
    public static final int SOAR_COOLDOWN_SECONDS = 20;

    // --- Бунт (D3): почет тікає біля сильнішого Беєндера Смерті ---
    public static final double REBELLION_RADIUS = 24.0;
    private static final double REBELLION_CHANCE_PER_LEVEL = 0.05;

    /** Кулдаун Мови мертвих (сек): Посл. 6 = 70, коротшає WEAK, не нижче підлоги. */
    public static int languageCooldownSeconds(Sequence sequence) {
        return shrink(LANGUAGE_COOLDOWN_SECONDS, LANGUAGE_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Скільки душа лишається поза тілом (сек): Посл. 6 = 5, росте MODERATE. */
    public static int languageDurationSeconds(Sequence sequence) {
        return (int) Math.round(LANGUAGE_DURATION_SECONDS
                * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Дальність Мови мертвих (блоки): Посл. 6 = 20, росте WEAK до стелі 32. */
    public static double languageRange(Sequence sequence) {
        return Math.min(LANGUAGE_RANGE_CAP, LANGUAGE_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Кулдаун Воскресіння (сек): Посл. 6 = 12, коротшає WEAK, не нижче підлоги. */
    public static int resurrectionCooldownSeconds(Sequence sequence) {
        return shrink(RESURRECTION_COOLDOWN_SECONDS, RESURRECTION_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Кулдаун Підкорення нежиті (сек): Посл. 6 = 45, коротшає WEAK, не нижче підлоги. */
    public static int subjugationCooldownSeconds(Sequence sequence) {
        return shrink(SUBJUGATION_COOLDOWN_SECONDS, SUBJUGATION_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Кулдаун Домовленості з духами (сек): Посл. 6 = 30, коротшає WEAK, не нижче підлоги. */
    public static int pactCooldownSeconds(Sequence sequence) {
        return shrink(PACT_COOLDOWN_SECONDS, PACT_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Радіус Підкорення (блоки): Посл. 6 = 12, росте WEAK до стелі 24. */
    public static double subjugationRadius(Sequence sequence) {
        return Math.min(SUBJUGATION_RADIUS_CAP,
                SUBJUGATION_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Скільки тримається щит обміну духом (сек): Посл. 6 = 30, росте MODERATE. */
    public static int spiritSwapWardSeconds(Sequence sequence) {
        return (int) Math.round(SPIRIT_SWAP_WARD_SECONDS
                * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Кулдаун обміну духом (сек): Посл. 6 = 90, коротшає WEAK, не нижче підлоги. */
    public static int spiritSwapCooldownSeconds(Sequence sequence) {
        return shrink(SPIRIT_SWAP_COOLDOWN_SECONDS, SPIRIT_SWAP_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /**
     * Шанс за секунду, що слуга зрадить провідника біля чужого Беєндера Смерті: 5% за кожну
     * Послідовність переваги суперника. Рівний чи слабший суперник почту не зачіпає (0).
     * Стелі немає й не треба: провідник — Посл. 6, тож розрив не буває більший за 6 (30%).
     */
    public static double rebellionChancePerSecond(Sequence guide, Sequence rival) {
        int levelsStronger = guide.level() - rival.level();
        if (levelsStronger <= 0) {
            return 0.0;
        }
        return REBELLION_CHANCE_PER_LEVEL * levelsStronger;
    }

    /** Кулдаун, що коротшає WEAK від бази до підлоги. */
    private static int shrink(int baseSeconds, int floorSeconds, Sequence sequence) {
        double seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(floorSeconds, Math.round(seconds));
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 6 (там рівно 1.0). */
    private static double growth(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(sequence.level(), strategy)
                / SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy);
    }
}
