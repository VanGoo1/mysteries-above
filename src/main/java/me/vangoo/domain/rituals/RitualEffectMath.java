package me.vangoo.domain.rituals;

/**
 * Балансні базові числа ефектів ритуалів (до Sequence-скейлу) та формула шансу
 * Дарування. Винесено з ефект-шару ({@code RitualEffectRunner}), бо балансна
 * математика не належить runner-у — див. правило pathway-abilities.
 */
public final class RitualEffectMath {

    public static final int LUCK_BASE_TICKS = 6000;             // 5 хв
    public static final int SANCTIFY_BASE_DURABILITY = 50;      // символічний ремонт, не повне відновлення
    public static final int EVENTS_BASE_WINDOW_SECONDS = 1800;  // 30 хв
    public static final int WALL_BASE_TICKS = 600;               // 30 с

    public static double bestowmentChance(int sequenceLevel) {
        return Math.min(0.9, 0.5 + 0.06 * (9 - sequenceLevel));
    }

    private RitualEffectMath() {
    }
}
