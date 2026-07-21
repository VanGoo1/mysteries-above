package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;

/**
 * Балансні числа пасивного кидка звичайного паперу (Seq 8 Клоуна шляху Блазня).
 *
 * <p>Кидок нічого не коштує, має короткий кулдаун і завдає шкоди, що зростає
 * зі силою Beyonder'а. Чиста математика — без Bukkit; ефект кидка живе в
 * {@code pathways.fool.abilities.PaperCutter} + {@code PaperThrowListener}.
 */
public final class PaperThrowDamage {

    /** Базова шкода кинджала на Seq 9 (до скейлу). */
    public static final int BASE_DAMAGE = 6;

    /** Кулдаун між кидками — 0.5с (10 тіків). */
    public static final int THROW_COOLDOWN_TICKS = 10;

    private PaperThrowDamage() {
    }

    /** Шкода одного паперового снаряда за поточною послідовністю. */
    public static int damageFor(Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.MODERATE);
        return (int) Math.ceil(BASE_DAMAGE * multiplier);
    }
}
