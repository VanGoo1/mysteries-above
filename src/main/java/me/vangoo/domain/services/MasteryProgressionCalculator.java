package me.vangoo.domain.services;

import me.vangoo.domain.valueobjects.Sequence;

public class MasteryProgressionCalculator {

    // Кількість духовності, необхідна для отримання 1.0 (одного цілого) пункту Mastery.
    // Тобто 1% від повного засвоєння.
    private static final double[] SPIRITUALITY_PER_ONE_PERCENT = {
            2000.0,  // Sequence 0 - Demigod (hardest)
            1500.0,  // Sequence 1 - Angel
            1200.0,  // Sequence 2 - Saint
            1000.0,  // Sequence 3 - Demi-Saint
            800.0,  // Sequence 4 - High-Sequence
            600.0,  // Sequence 5 - Mid-Sequence (виправив з 400 на 200 для балансу, як у логіці прогресії)
            400.0,  // Sequence 6
            300.0,  // Sequence 7
            170.0,   // Sequence 8
            60.0    // Sequence 9 - Starter - 6,000 total (60 * 100)
    };

    /**s
     * Повертає вартість 1 пункту (1%) Mastery для вказаної послідовності.
     */
    public static double getSpiritualityPerOnePercent(Sequence sequence) {
        int level = Math.min(9, Math.max(0, sequence.level()));
        return SPIRITUALITY_PER_ONE_PERCENT[level];
    }

    /**
     * Розраховує приріст Mastery (у дробових числах) на основі витраченої духовності.
     * Тепер повертає double, наприклад 0.83, 1.5, 0.05 тощо.
     */
    public static double calculateMasteryGain(int spiritualitySpent, Sequence sequence) {
        if (spiritualitySpent <= 0) {
            return 0.0;
        }

        double costPerPoint = getSpiritualityPerOnePercent(sequence);

        // Перетворюємо в double для точного ділення
        // Приклад: cost=60, spent=30 -> результат 0.5 (піввідсотка)
        return (double) spiritualitySpent / costPerPoint;
    }

    /**
     * Повертає загальну кількість духовності для проходження від 0 до 100%.
     */
    public static double getTotalSpiritualityForFullMastery(Sequence sequence) {
        return getSpiritualityPerOnePercent(sequence) * 100.0;
    }
}