package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;

/**
 * Радіус «Бачення ниток» (Seq 5 Маріонетника шляху Блазня): нитки над головами
 * істот + виявлення невидимих сущностей поблизу. База 24 блоки, росте зі силою.
 * Обмежений заради продуктивності. Чиста математика.
 */
public final class ThreadVisionRange {

    private static final int BASE_RANGE = 24;
    private static final int MAX_RANGE = 40;

    private ThreadVisionRange() {
    }

    public static int rangeFor(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.WEAK);
        return Math.min(MAX_RANGE, (int) Math.round(BASE_RANGE * multiplier));
    }
}
