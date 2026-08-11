package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 7 шляху Помилки (Криптолог).
 *
 * <p>Одне джерело правди для Дешифрування та для тіру доказів у Вищій спостережливості.
 * Чиста математика — без Bukkit; ефекти живуть у {@code pathways.error.abilities}.
 *
 * <p>Ключове правило вікі: Дешифрування — не ворожіння. Глибина відповіді залежить від
 * того, скільки слідів реально існує ({@link #depthFor}), а не від удачі каста.
 */
public final class CryptologistInsight {

    /** Послідовність, на якій здібність з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 7;

    private CryptologistInsight() {
    }

    // --- Вартість і дальності ---
    public static final int DECRYPTION_COST = 110;
    public static final int DECRYPTION_COOLDOWN_SECONDS = 45;
    private static final int DECRYPTION_COOLDOWN_FLOOR_SECONDS = 20;

    /** Дальність пошуку цілі/блока в прицілі (режими A і B). */
    public static final double CROSSHAIR_RANGE = 20.0;
    /** Радіус сканування містицизму (режим D). */
    public static final double ZONE_RADIUS = 24.0;
    /** Радіус і вікно (сек) запиту історії CoreProtect. */
    public static final int TRACE_LOOKUP_RADIUS = 8;
    public static final int TRACE_LOOKUP_WINDOW_SECONDS = 600;

    /** Пасивне маркування доказів (Seq 7 тір Вищої спостережливості). */
    public static final double EVIDENCE_MARK_RADIUS = 8.0;
    public static final int EVIDENCE_MARK_INTERVAL_SECONDS = 15;

    /** Тривалість замовляння з режиму C (сек). */
    public static final int INCANTATION_DURATION_SECONDS = 180;

    /** Скільки слідів потрібно для глибини 1/2/3 на Seq 7. */
    private static final int[] DEPTH_BASE_THRESHOLDS = {2, 5, 9};
    /** Втрата глузду за глибину 1/2/3 на Seq 7. */
    private static final int[] DEPTH_BASE_SANITY = {0, 2, 5};

    /** Кулдаун (сек): Seq 7 = 45, коротшає MODERATE, не нижче підлоги. */
    public static int decryptionCooldownSeconds(Sequence sequence) {
        double seconds = DECRYPTION_COOLDOWN_SECONDS * relative(sequence, ScalingStrategy.MODERATE);
        return (int) Math.max(DECRYPTION_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /**
     * Скільки слідів треба, щоб дістати глибину {@code depth} (1..3).
     * Пороги падають, коли Криптолог сильнішає.
     */
    public static int depthThreshold(int depth, Sequence sequence) {
        requireDepth(depth);
        int base = DEPTH_BASE_THRESHOLDS[depth - 1];
        int easier = Math.max(0, SequenceScaler.getSequencePower(sequence.level())
                - SequenceScaler.getSequencePower(BASE_SEQUENCE));
        return Math.max(depth, base - (int) Math.round(easier * base * 0.06));
    }

    /** Глибина відповіді за наявними доказами: 0 = нічого не вичитати, 3 = вся правда. */
    public static int depthFor(int evidenceCount, Sequence sequence) {
        for (int depth = 3; depth >= 1; depth--) {
            if (evidenceCount >= depthThreshold(depth, sequence)) {
                return depth;
            }
        }
        return 0;
    }

    /** Втрата глузду за глибокий читання: d1 = 0, d2 = 2, d3 = 5 на Seq 7, росте WEAK. */
    public static int sanityLoss(int depth, Sequence sequence) {
        if (depth <= 0) {
            return 0;
        }
        requireDepth(depth);
        double base = DEPTH_BASE_SANITY[depth - 1];
        return (int) Math.round(base / relative(sequence, ScalingStrategy.WEAK));
    }

    /** Множник шкоди від замовляння (режим C, глибина 3): Seq 7 = 1.20, росте WEAK. */
    public static double incantationDamageMultiplier(Sequence sequence) {
        return 1.0 + 0.20 / relative(sequence, ScalingStrategy.WEAK);
    }

    /** Відношення сили на Seq 7 до сили на заданій Послідовності (≤ 1.0 для сильніших). */
    private static double relative(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
                / SequenceScaler.calculateMultiplier(sequence.level(), strategy);
    }

    private static void requireDepth(int depth) {
        if (depth < 1 || depth > 3) {
            throw new IllegalArgumentException("Depth must be 1..3, got: " + depth);
        }
    }
}
