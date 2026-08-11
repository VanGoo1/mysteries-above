package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 6 шляху Помилки (Прометей).
 *
 * <p>Одне джерело правди для Крадіжки сили, для Прометеєвого тіру Крадіжки матеріалів і
 * для тіру скарбів у Вищій спостережливості. Чиста математика — без Bukkit; ефекти живуть
 * у {@code pathways.error.abilities}.
 *
 * <p>Ключове правило вікі: «чим краще розумієш ціль, тим легше вкрасти саме те, що треба».
 * Модельовано через {@link #successChance} — розблоковані рецепти шляху жертви піднімають
 * шанс, сильніша жертва його опускає.
 */
public final class PrometheusTheft {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 6;

    private PrometheusTheft() {
    }

    // --- Крадіжка сили ---
    public static final int POWER_THEFT_COST = 200;
    public static final int POWER_THEFT_COOLDOWN_SECONDS = 200;
    private static final int POWER_THEFT_COOLDOWN_FLOOR_SECONDS = 120;
    public static final double POWER_THEFT_RANGE = 50.0;

    /** Скільки вкрадена здібність живе у злодія (реальний час). */
    public static final long STOLEN_DURATION_MILLIS = 10L * 60_000L;
    /** Скільки жертва не може кастувати вкрадену здібність (реальний час, від моменту крадіжки). */
    public static final long SUPPRESSION_DURATION_MILLIS = 30L * 60_000L;
    /** Одна вкрадена здібність за раз — власний слот, незалежний від {@code Analysis}. */
    public static final int MAX_CONCURRENT_STOLEN = 1;

    // --- Шанс успіху ---
    private static final double BASE_CHANCE = 0.40;
    private static final double CHANCE_PER_RECIPE = 0.03;
    private static final double MAX_RECIPE_BONUS = 0.25;
    private static final double CHANCE_PER_VICTIM_LEVEL = 0.08;
    private static final double MIN_CHANCE = 0.10;
    private static final double MAX_CHANCE = 0.95;

    // --- Прометеїв тір успадкованих здібностей ---
    /** Дальність Крадіжки матеріалів на Seq 6 (проти 5 на Seq 8/7). */
    public static final double MATERIAL_THEFT_RANGE = 50.0;
    /** Радіус пошуку полум'я для крадіжки вогню (вікі 3b.1) — куб по блоках, тож малий. */
    public static final double FIRE_THEFT_RADIUS = 10.0;
    /** Скільки секунд вогнетривкості дає вкрадене полум'я. */
    public static final int FIRE_THEFT_RESISTANCE_SECONDS = 20;
    /** Радіус чуття цінностей: контейнери, дропи, чужі інвентарі. */
    public static final double VALUABLES_RADIUS = 50.0;
    /** Радіус сканування руди (менший — це кубічний прохід по блоках). */
    public static final double ORE_SCAN_RADIUS = 16.0;
    /** Від скількох цінних одиниць схованка вважається багатою. */
    public static final int TREASURE_RICH = 10;
    /** Від скількох — середньою; менше — дрібниця. */
    public static final int TREASURE_MEDIUM = 3;

    // --- Стійкість розуму ---
    /** Скільки лишається від вхідної втрати глузду під Стійкістю розуму. */
    public static final double MENTAL_FORTITUDE_SANITY_MULTIPLIER = 0.5;
    /** Раз на стільки секунд Стійкість розуму сама відновлює 1 глузду. */
    public static final int MENTAL_FORTITUDE_RECOVERY_SECONDS = 45;

    /** Кулдаун Крадіжки сили (сек): Seq 6 = 300, коротшає MODERATE, не нижче підлоги. */
    public static int powerTheftCooldownSeconds(Sequence sequence) {
        double seconds = POWER_THEFT_COOLDOWN_SECONDS * relative(sequence, ScalingStrategy.MODERATE);
        return (int) Math.max(POWER_THEFT_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /**
     * Шанс успішної крадіжки сили.
     *
     * @param unlockedVictimPathwayRecipes скільки рецептів шляху жертви злодій уже відкрив
     * @param victimSequence               Послідовність жертви
     * @return шанс у межах [0.10, 0.95]
     */
    public static double successChance(int unlockedVictimPathwayRecipes, Sequence victimSequence) {
        double knowledge = Math.min(MAX_RECIPE_BONUS,
                Math.max(0, unlockedVictimPathwayRecipes) * CHANCE_PER_RECIPE);
        int levelsStronger = Math.max(0, BASE_SEQUENCE - victimSequence.level());
        double resistance = levelsStronger * CHANCE_PER_VICTIM_LEVEL;
        return clamp(BASE_CHANCE + knowledge - resistance, MIN_CHANCE, MAX_CHANCE);
    }

    /** Чи покаже меню справжню назву здібності жертви (вікі 3a.2). */
    public static boolean revealsAbilityNames(int unlockedVictimPathwayRecipes) {
        return unlockedVictimPathwayRecipes > 0;
    }

    /** Скільки глузду перетікає від жертви до злодія в слоті «Божевілля»: Seq 6 = 3, росте WEAK. */
    public static int corruptionTransfer(Sequence sequence) {
        return (int) Math.round(3.0 / relative(sequence, ScalingStrategy.WEAK));
    }

    /** Відношення сили на Seq 6 до сили на заданій Послідовності (≤ 1.0 для сильніших). */
    private static double relative(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
                / SequenceScaler.calculateMultiplier(sequence.level(), strategy);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
