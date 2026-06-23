package me.vangoo.domain.valueobjects;

/**
 * Чисте правило балансу: підсумковий шанс успіху гадання проти цілі з опором (Anti-Divination).
 * <p>
 * Бере «сирий» шанс із {@link SequenceBasedSuccessChance} (різниця Sequence) і накладає
 * додаткові коригування:
 * <ul>
 *   <li>якщо кастер у перевазі — невеликий штраф, але не нижче підлоги;</li>
 *   <li>якщо кастер слабший — динамічне падіння, плюс окреме обмеження на велику різницю.</li>
 * </ul>
 * Жодного Bukkit, RNG чи повідомлень — це детермінована математика, яку перевіряє
 * {@code DivinationOddsTest}. Сам кидок ({@code rng < probability}) і вивід відсотка
 * лишаються в шарі ефектів.
 */
public record DivinationOdds(int casterSequence, int targetSequence) {

    private static final double ADVANTAGED_FLOOR = 0.75;
    private static final double ADVANTAGED_MAX_PENALTY = 0.2;
    private static final double ADVANTAGED_PENALTY_PER_STEP = 0.03;

    private static final double DISADVANTAGED_MIN_FACTOR = 0.05;
    private static final double DISADVANTAGED_DROP_PER_STEP = 0.35;
    private static final double LARGE_DIFFERENCE_CAP_FACTOR = 0.5;

    /**
     * Імовірність успіху в діапазоні [0.0, 1.0].
     */
    public double successProbability() {
        SequenceBasedSuccessChance base = new SequenceBasedSuccessChance(casterSequence, targetSequence);
        double baseChance = base.calculateChance();
        int diff = base.getSequenceDifference();

        double finalChance;
        if (base.isCasterAdvantaged()) {
            double penalty = Math.min(ADVANTAGED_MAX_PENALTY, diff * ADVANTAGED_PENALTY_PER_STEP);
            finalChance = Math.max(ADVANTAGED_FLOOR, baseChance * (1.0 - penalty));
        } else {
            double dynamic = 1.0 - DISADVANTAGED_DROP_PER_STEP * diff;
            finalChance = baseChance * Math.max(DISADVANTAGED_MIN_FACTOR, dynamic);
            if (base.isLargeDifference()) {
                finalChance = Math.min(finalChance, LARGE_DIFFERENCE_CAP_FACTOR * baseChance);
            }
        }

        return clampToUnit(finalChance);
    }

    /**
     * Готовий до показу відсоток (наприклад {@code "94%"}).
     */
    public String formattedPercent() {
        return String.format("%.0f%%", successProbability() * 100);
    }

    private static double clampToUnit(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
