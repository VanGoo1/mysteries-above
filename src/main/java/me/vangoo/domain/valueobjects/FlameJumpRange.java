package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;

/**
 * Дальність телепорту «Стрибок крізь полум'я» (Seq 7 Фокусника шляху Блазня).
 * База 30 блоків, зростає зі силою. Чиста математика — без Bukkit.
 */
public final class FlameJumpRange {

    public static final int BASE_RANGE = 30;
    private static final int MAX_RANGE = 60;

    private FlameJumpRange() {
    }

    public static int rangeFor(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.WEAK);
        return Math.min(MAX_RANGE, (int) Math.round(BASE_RANGE * multiplier));
    }
}
