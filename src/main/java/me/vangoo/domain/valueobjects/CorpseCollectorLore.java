package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 9 шляху Смерті (Трупозбирач).
 *
 * <p>Одне джерело правди для всіх п'яти здібностей тіру: Знання (Нежить) — мітка слабкого
 * місця й розтин трупа, Духовний зір, Бачення духів, Стійкість до трупних аур. Чиста
 * математика — без Bukkit; ефекти живуть у {@code pathways.death.abilities}.
 *
 * <p>Посл. 9 — найслабша, тож усі базові числа задані саме для неї, а
 * {@link #growth(Sequence, ScalingStrategy)} лише піднімає їх для сильніших Послідовностей
 * (на Посл. 9 множник рівно 1.0).
 */
public final class CorpseCollectorLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 9;

    private CorpseCollectorLore() {
    }

    // --- Тіло трупозбирача (PhysicalEnhancement) ---
    /** База розрахунку бонусного HP; potion-ефектів тір не дає — тіло холодне, не швидке. */
    public static final int PHYSIQUE_HP_BASE = 3;

    // --- Знання (Нежить): спільні для обох режимів каста ---
    public static final int KNOWLEDGE_COST = 40;
    private static final int KNOWLEDGE_COOLDOWN_SECONDS = 12;
    private static final int KNOWLEDGE_COOLDOWN_FLOOR_SECONDS = 4;

    // --- Режим «мітка слабкого місця» (лише по нежиті) ---
    private static final double MARK_RANGE = 20.0;
    private static final double MARK_RANGE_CAP = 60.0;
    private static final int MARK_DURATION_SECONDS = 20;
    /** Надбавка до урону понад 1.0: Посл. 9 = +35%. */
    private static final double MARK_DAMAGE_BONUS = 0.35;
    private static final double MARK_DAMAGE_MULTIPLIER_CAP = 2.5;
    /** Мітка одна на кастера — повторний каст переносить її на нову ціль. */
    public static final int MAX_CONCURRENT_MARKS = 1;

    // --- Режим «розтин трупа» (DEATH-події з CoreProtect) ---
    private static final double AUTOPSY_RADIUS = 10.0;
    private static final double AUTOPSY_RADIUS_CAP = 40.0;
    private static final int AUTOPSY_WINDOW_SECONDS = 30 * 60;
    private static final int AUTOPSY_MAX_RECORDS = 5;

    // --- Духовний зір (тогл) ---
    private static final double SPIRIT_VISION_RANGE = 20.0;
    private static final double SPIRIT_VISION_RANGE_CAP = 60.0;
    /** Періодична ціна тогла за секунду; від Послідовності не залежить. */
    public static final int SPIRIT_VISION_PERIODIC_COST = 3;

    // --- Бачення духів (постійне, безкоштовне) ---
    private static final double DEATH_SIGHT_RANGE = 16.0;
    private static final double DEATH_SIGHT_RANGE_CAP = 48.0;

    // --- Похмура присутність ---
    /**
     * Скільки секунд нежить пам'ятає, що трупозбирач ударив першим. Поки пам'ятає — агриться
     * як на будь-кого; коли забуде, похмура присутність знову робить його «своїм».
     * Від Послідовності не залежить: це правило поведінки, не сила.
     */
    public static final int PROVOKE_MEMORY_SECONDS = 10;
    private static final double GLOOM_RADIUS = 24.0;
    private static final double GLOOM_RADIUS_CAP = 64.0;

    // --- Стійкість до трупних аур ---
    /** У скільки разів швидше згасають Тління й Їдкість: Посл. 9 = вдвічі. */
    private static final double DECAY_ACCELERATION = 2.0;

    /** Кулдаун Знання (сек): Посл. 9 = 12, коротшає WEAK, не нижче підлоги. */
    public static int knowledgeCooldownSeconds(Sequence sequence) {
        double seconds = KNOWLEDGE_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(KNOWLEDGE_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /** Дальність прицілу мітки (блоки): Посл. 9 = 20, росте WEAK до стелі 60. */
    public static double markRange(Sequence sequence) {
        return Math.min(MARK_RANGE_CAP, MARK_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Скільки мітка тримається (сек): Посл. 9 = 20, росте MODERATE. */
    public static int markDurationSeconds(Sequence sequence) {
        return (int) Math.round(MARK_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE));
    }

    /**
     * Множник урону кастера по міченій нежиті: Посл. 9 = 1.35, надбавка росте WEAK
     * до стелі 2.5. По не-нежиті мітка не ставиться взагалі — множника тут немає.
     */
    public static double markDamageMultiplier(Sequence sequence) {
        double bonus = MARK_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK);
        return Math.min(MARK_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus);
    }

    /** Радіус читання місця смерті (блоки): Посл. 9 = 10, росте WEAK до стелі 40. */
    public static double autopsyRadius(Sequence sequence) {
        return Math.min(AUTOPSY_RADIUS_CAP, AUTOPSY_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Як давно могла статись смерть, щоб слід іще читався (сек): Посл. 9 = 30 хв, росте MODERATE. */
    public static int autopsyWindowSeconds(Sequence sequence) {
        return (int) Math.round(AUTOPSY_WINDOW_SECONDS * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Скільки смертей показує один розтин: Посл. 9 = 5, росте WEAK. */
    public static int autopsyMaxRecords(Sequence sequence) {
        return (int) Math.round(AUTOPSY_MAX_RECORDS * growth(sequence, ScalingStrategy.WEAK));
    }

    /**
     * Радіус, у якому похмура присутність змушує нежить забути вже взяту ціль (блоки):
     * Посл. 9 = 24, росте WEAK до стелі 64.
     */
    public static double gloomRadius(Sequence sequence) {
        return Math.min(GLOOM_RADIUS_CAP, GLOOM_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Радіус Духовного зору (блоки): Посл. 9 = 20, росте WEAK до стелі 60. */
    public static double spiritVisionRange(Sequence sequence) {
        return Math.min(SPIRIT_VISION_RANGE_CAP,
                SPIRIT_VISION_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Радіус постійного Бачення духів (блоки): Посл. 9 = 16, росте WEAK до стелі 48. */
    public static double deathSightRange(Sequence sequence) {
        return Math.min(DEATH_SIGHT_RANGE_CAP,
                DEATH_SIGHT_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /**
     * У скільки разів швидше згасають Тління (Wither) й Їдкість (Poison) на кастері:
     * Посл. 9 = 2.0, росте WEAK. Холод (Freezing) до цієї математики не входить —
     * імунітет до нього абсолютний з першої ж Послідовності.
     */
    public static double decayAcceleration(Sequence sequence) {
        return DECAY_ACCELERATION * growth(sequence, ScalingStrategy.WEAK);
    }

    /**
     * Скільки зайвих тіків Тління/Їдкості з'їдається за кожну секунду носіння ефекту.
     * Прискорення 2.0 означає «за секунду згорає дві» — тобто мінус ще 20 тіків понад
     * ванільний хід годинника.
     */
    public static int decayExtraTicksPerSecond(Sequence sequence) {
        return (int) Math.round(20 * (decayAcceleration(sequence) - 1.0));
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 9 (там рівно 1.0). */
    private static double growth(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(sequence.level(), strategy)
                / SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy);
    }
}
