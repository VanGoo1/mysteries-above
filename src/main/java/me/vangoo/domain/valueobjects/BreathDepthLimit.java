package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;

/**
 * Гранична глибина «вічного» підводного дихання (Seq 7 Фокусника шляху Блазня).
 *
 * <p>Потойбічний дихає під водою скільки завгодно, ПОКИ не занурився глибше
 * порогу від поверхні води (база 5 блоків, зростає зі силою). Глибше — кисень
 * витрачається як звичайно. Чиста математика — без Bukkit.
 */
public final class BreathDepthLimit {

    public static final int BASE_DEPTH = 5;
    private static final int MAX_DEPTH = 12;

    private BreathDepthLimit() {
    }

    /** Максимальна глибина (у блоках від поверхні), на якій дихання ще діє. */
    public static int maxDepth(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.WEAK);
        return Math.min(MAX_DEPTH, (int) Math.round(BASE_DEPTH * multiplier));
    }
}
