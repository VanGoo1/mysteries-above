package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;

/**
 * Тайминги фаз Контролю Маріонетки (Seq 5 Маріонетника шляху Блазня).
 *
 * <p>Фаза 1 (фіксація ниток) — 5…20с залежно від засвоєння кастера. Фаза 2
 * (конверсія) — для гравця база 5 хв, сильно скейлиться з силою; для моба ~15с.
 * Чиста математика — без Bukkit.
 */
public final class MarionetteTiming {

    private static final double PHASE1_MAX_SECONDS = 20.0;
    private static final double PHASE1_MIN_SECONDS = 5.0;
    private static final int PLAYER_CONVERT_BASE_TICKS = 6000; // 5 хв на Seq 9
    private static final int MOB_SWAP_BASE_TICKS = 300;        // 15с на Seq 9

    /** Поріг разового урону по жертві, що збиває фіксацію. */
    public static final double BREAK_DAMAGE_THRESHOLD = 6.0;

    private MarionetteTiming() {
    }

    /**
     * Тривалість фази фіксації в тіках за засвоєнням (0…100): 20с при 0%,
     * лінійно до 5с при 100%.
     */
    public static int phase1LockTicks(double masteryPercent) {
        double clamped = Math.max(0.0, Math.min(100.0, masteryPercent));
        double seconds = PHASE1_MAX_SECONDS
                - (PHASE1_MAX_SECONDS - PHASE1_MIN_SECONDS) * (clamped / 100.0);
        return (int) Math.round(seconds * 20);
    }

    /** Тривалість фази конверсії гравця (тіки): база 5 хв, швидшає зі силою (STRONG). */
    public static int playerConvertTicks(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.STRONG);
        return (int) Math.round(PLAYER_CONVERT_BASE_TICKS / multiplier);
    }

    /** Тривалість фази перетворення моба (тіки): ~15с, швидшає зі силою (WEAK). */
    public static int mobSwapTicks(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.WEAK);
        return (int) Math.round(MOB_SWAP_BASE_TICKS / multiplier);
    }
}
