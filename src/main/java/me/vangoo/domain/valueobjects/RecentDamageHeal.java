package me.vangoo.domain.valueobjects;

/**
 * Балансна формула зцілення на основі недавно отриманої шкоди
 * (Seq 7 Фокусника шляху Блазня — реворк «Перенос шкоди»).
 *
 * <p>Лікує частку шкоди, отриманої за вікно, не більше стелі, що зростає зі
 * силою. НЕ лікує до фулл — це «загоєння» половини рани. Чиста математика.
 */
public final class RecentDamageHeal {

    /** Вікно, за яке рахується недавня шкода. */
    public static final long DAMAGE_WINDOW_MS = 10_000L;

    private static final double HEAL_FRACTION = 0.5;
    private static final int BASE_CEILING = 8;

    private RecentDamageHeal() {
    }

    /** 0 (Seq 9) … 9 (Seq 0). */
    private static int power(Sequence sequence) {
        return 9 - sequence.level();
    }

    /** Стеля разового зцілення (HP) за поточною послідовністю. */
    public static double healCeiling(Sequence sequence) {
        return BASE_CEILING + power(sequence);
    }

    /** Скільки HP відновлює здібність із {@code recentDamage}, отриманих за вікно. */
    public static double healAmount(double recentDamage, Sequence sequence) {
        if (recentDamage <= 0) return 0;
        return Math.min(recentDamage * HEAL_FRACTION, healCeiling(sequence));
    }
}
