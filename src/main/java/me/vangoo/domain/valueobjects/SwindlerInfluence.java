package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 8 шляху Помилки (Аферист).
 *
 * <p>Одне джерело правди для всіх семи здібностей Seq 8: чарівність, красномовність,
 * підміна думок, ментальний розлад, вища спостережливість і крадіжка матеріалів.
 * Чиста математика — без Bukkit; ефекти живуть у {@code pathways.error.abilities}.
 */
public final class SwindlerInfluence {

    private SwindlerInfluence() {
    }

    // --- Вартість духовності та базові кулдауни (сек) ---
    public static final int ELOQUENCE_COST = 35;
    public static final int ELOQUENCE_COOLDOWN_SECONDS = 20;
    public static final int MISDIRECTION_COST = 30;
    public static final int MISDIRECTION_COOLDOWN_SECONDS = 30;
    public static final int DISRUPTION_COST = 45;
    public static final int DISRUPTION_COOLDOWN_SECONDS = 60;
    public static final int MATERIAL_THEFT_COST = 60;

    // --- Дальності (блоки) ---
    public static final double CHARM_AGGRO_RANGE = 16.0;
    public static final double ELOQUENCE_RANGE = 12.0;
    public static final double MISDIRECTION_CONE_RANGE = 10.0;
    public static final double MISDIRECTION_CONE_ANGLE_DEGREES = 90.0;
    public static final double DISRUPTION_RANGE = 15.0;
    public static final double OBSERVATION_GAZE_RANGE = 15.0;
    /** Радіус чуття цінностей; вікі розширює його до 50 лише на Прометеї (Seq 6). */
    public static final double OBSERVATION_VALUABLES_RADIUS = 10.0;
    public static final double MATERIAL_THEFT_RANGE = 5.0;

    /** Шанс гравця-жертви отримати перемішаний хотбар від Підміни думок. */
    public static final double MISDIRECTION_SCRAMBLE_CHANCE = 0.70;

    /** 0 (Seq 9) … 9 (Seq 0). */
    private static int power(Sequence sequence) {
        return SequenceScaler.getSequencePower(sequence.level());
    }

    private static double scaled(double base, Sequence sequence, ScalingStrategy strategy) {
        return base * SequenceScaler.calculateMultiplier(sequence.level(), strategy);
    }

    /**
     * Шанс, що вороже створіння не зможе взяти Афериста на приціл.
     * Seq 8 = 25%, зростає повільно (WEAK) зі стелею 40%.
     */
    public static double charmAggroCancelChance(Sequence sequence) {
        return clamp(scaled(0.25, sequence, ScalingStrategy.WEAK), 0.0, 0.40);
    }

    /** Знижка в торгівлі з селянами: Seq 8 = 10%, стеля 30%. */
    public static double charmTradeDiscount(Sequence sequence) {
        return clamp(0.10 + power(sequence) * 0.025, 0.0, 0.30);
    }

    /** Тривалість придушення агресії (тіки). Seq 8 = 8 с, росте сильно (STRONG). */
    public static int eloquenceDurationTicks(Sequence sequence) {
        return ticks(scaled(8.0, sequence, ScalingStrategy.STRONG));
    }

    /** Тривалість перенацілювання в конусі (тіки). Seq 8 = 6 с. */
    public static int misdirectionDurationTicks(Sequence sequence) {
        return ticks(scaled(6.0, sequence, ScalingStrategy.MODERATE));
    }

    /** Тривалість фальшивої реальності (тіки). Seq 8 = 8 с. */
    public static int disruptionDurationTicks(Sequence sequence) {
        return ticks(scaled(8.0, sequence, ScalingStrategy.MODERATE));
    }

    /** Втрата глузду жертви-Beyonder'а від Ментального розладу. Seq 8 = 3. */
    public static int disruptionSanityLoss(Sequence sequence) {
        return (int) Math.round(scaled(3.0, sequence, ScalingStrategy.WEAK));
    }

    /** Кулдаун Крадіжки матеріалів (сек): Seq 8 = 40 с, не коротше 10 с. */
    public static int materialTheftCooldownSeconds(Sequence sequence) {
        double seconds = 40.0 / SequenceScaler.calculateMultiplier(sequence.level(), ScalingStrategy.MODERATE);
        return (int) Math.max(10, Math.round(seconds));
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
