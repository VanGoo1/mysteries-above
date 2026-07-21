package me.vangoo.domain.valueobjects;

/**
 * Балансні числа паперових ляльок-замін (Seq 7 Фокусника шляху Блазня).
 *
 * <p>Каст створює партію ляльок (базово 3, до 6 зі силою), списуючи папір.
 * Ляльки накопичуються до стелі й поглинають важкі удари. Чиста математика.
 */
public final class DollBatch {

    /** Скільки паперу коштує одна лялька в партії. */
    public static final int PAPER_PER_DOLL = 3;

    /** Поріг «важкого» удару, який лялька поглинає (HP). */
    public static final double DAMAGE_THRESHOLD = 4.0;

    /** Радіус випадкового телепорту після поглинання (блоки). */
    public static final int TELEPORT_RADIUS = 6;

    private static final int BASE_DOLLS = 3;
    private static final int MAX_DOLLS_PER_CAST = 6;

    private DollBatch() {
    }

    /** 0 (Seq 9) … 9 (Seq 0). */
    private static int power(Sequence sequence) {
        return 9 - sequence.level();
    }

    /** Скільки ляльок створює один каст за поточною послідовністю (3…6). */
    public static int dollsPerCast(Sequence sequence) {
        int dolls = BASE_DOLLS + (int) Math.floor(power(sequence) * 0.75);
        return Math.min(MAX_DOLLS_PER_CAST, dolls);
    }

    /** Скільки паперу коштує один каст. */
    public static int paperCost(Sequence sequence) {
        return dollsPerCast(sequence) * PAPER_PER_DOLL;
    }

    /** Стеля накопичення ляльок (можна відкласти більше однієї партії). */
    public static int maxStored(Sequence sequence) {
        return dollsPerCast(sequence) + 3;
    }
}
