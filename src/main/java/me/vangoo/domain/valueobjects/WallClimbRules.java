package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;

/**
 * Балансні числа лазіння по стінах (Seq 8 Клоуна шляху Блазня — акробатика).
 *
 * <p>Коли клоун притиснутий до вертикальної стіни й дивиться в неї, він
 * повільно дереться вгору. Швидкість підйому зростає зі силою. Чиста
 * математика — ефект (перевірка стіни, {@code setVelocity}) у
 * {@code pathways.fool.abilities.ClownAgility}.
 */
public final class WallClimbRules {

    /** Базова вертикальна швидкість лазіння (блоків/тік) на Seq 9. */
    private static final double BASE_CLIMB_SPEED = 0.18;

    /** Максимальна дистанція до стіни, щоб рахувати «притиснутий» (блоки). */
    public static final double WALL_REACH = 0.9;

    private WallClimbRules() {
    }

    /** Вертикальна швидкість підйому по стіні за поточною послідовністю (з м'якою стелею). */
    public static double climbSpeed(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.WEAK);
        return Math.min(0.35, BASE_CLIMB_SPEED * multiplier);
    }
}
