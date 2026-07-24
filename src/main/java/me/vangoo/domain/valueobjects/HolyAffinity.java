package me.vangoo.domain.valueobjects;

import java.util.Set;

/**
 * Класифікація pathway як "темного/нежиті" для святого урону Sun.
 * Набір pathways — калібрувальна константа (тюнінг-важіль), не архітектурне рішення.
 */
public final class HolyAffinity {

    private static final Set<String> DARK_PATHWAYS = Set.of(
            "Death", "Darkness", "Chained", "HangedMan", "Abyss"
    );

    private static final double DARK_MULTIPLIER = 1.0;
    private static final double NEUTRAL_MULTIPLIER = 0.6;

    private HolyAffinity() {
    }

    public static boolean isDark(String pathwayName) {
        return pathwayName != null && DARK_PATHWAYS.contains(pathwayName);
    }

    public static double damageMultiplier(String pathwayName) {
        return damageMultiplier(isDark(pathwayName));
    }

    /** Для цілей поза pathway-класифікацією (ванільні нежить-моби) — класифікація на боці ефекту. */
    public static double damageMultiplier(boolean dark) {
        return dark ? DARK_MULTIPLIER : NEUTRAL_MULTIPLIER;
    }
}
