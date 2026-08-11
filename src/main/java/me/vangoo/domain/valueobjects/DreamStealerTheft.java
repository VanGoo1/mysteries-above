package me.vangoo.domain.valueobjects;

/**
 * Балансні числа Послідовності 5 шляху Помилки (Крадій снів).
 *
 * <p>Одне джерело правди для Концептуальної крадіжки (шість режимів), для Посл-5 тіру
 * Крадіжки сили, для пасивного розвіювання ілюзій і для Личини. Чиста математика — без
 * Bukkit; ефекти живуть у {@code pathways.error.abilities}.
 *
 * <p>Числа Посл. 6 лишаються в {@link PrometheusTheft} і сюди не переїжджають: там —
 * Прометей, тут — Крадій снів. Спільне лише те, що обидва тіри пишуться в один ledger.
 */
public final class DreamStealerTheft {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 5;

    private DreamStealerTheft() {
    }

    // --- Спільне для всіх режимів Концептуальної крадіжки ---
    /** Вікі: «Theft ... within 80 meters range». */
    public static final double CONCEPTUAL_RANGE = 80.0;
    public static final int CONCEPTUAL_COST = 200;
    public static final int CONCEPTUAL_COOLDOWN_SECONDS = 5;
    /** Один «тривалий» злам за раз — той самий слот, що й у Прометея. */
    public static final int MAX_CONCURRENT_STOLEN = 1;
    /** Скільки глузду коштує вдала концептуальна крадіжка. */
    public static final int CONCEPTUAL_CORRUPTION = 1;

    // --- Крадіжка сили, тір Посл. 5 (вікі 3b.5) ---
    /** 50 м на Посл. 6 → 80 м тут. */
    public static final double POWER_THEFT_RANGE = CONCEPTUAL_RANGE;
    /** «can be used for half an hour» — проти 10 хв Прометея. */
    public static final long POWER_STOLEN_DURATION_MILLIS = 30L * 60_000L;
    /** Жертва не кличе вкрадене вдвічі довше, ніж злодій ним володіє. */
    public static final long POWER_SUPPRESSION_DURATION_MILLIS = 60L * 60_000L;
    /**
     * «If ... has not yet used the Stolen power ... it can be kept for a week» — тиждень
     * стиснуто до 6 год реального часу (рішення 3 плану): слот увесь цей час зайнятий.
     */
    public static final long POWER_UNUSED_HOLD_MILLIS = 6L * 60L * 60_000L;

    // --- Режим «Сон» (B1.1) ---
    /** Скільки злодій тримає чужий сон (і скільки жертва не може лягти в ліжко). */
    public static final long DREAM_HOLD_MILLIS = 30L * 60_000L;
    /** Скільки триває чужий сон, влитий у третю особу (вікі: «infuse scenes within the Dream»). */
    public static final int DREAM_INFUSION_SECONDS = 15;

    // --- Режим «Загальні здатності» (B1.3) ---
    /** Ходити / літати / кастувати — забрано на цей строк. */
    public static final int GENERAL_ABILITY_SECONDS = 20;

    // --- Режим «Атака» (B1.4) ---
    /** Вікно, у якому перший вхідний удар поглинається цілком. */
    public static final int ATTACK_WINDOW_SECONDS = 5;
    /** Скільки заряд живе після поглинання, доки злодій не вдарить. */
    public static final int ATTACK_CHARGE_SECONDS = 10;
    /** Стеля поверненої шкоди — щоб зал п'ятдесяткою не поверталась. */
    public static final double ATTACK_CHARGE_CAP = 20.0;

    // --- Режим «Серце» (B1.6) ---
    /** 4 серця = 8.0 одиниць максимального здоров'я, перенесених від жертви до злодія. */
    public static final double HEART_HEALTH_TRANSFER = 8.0;
    public static final int HEART_DURATION_SECONDS = 60;
    /** Жертві лишається щонайменше серце: крадіжка — не вбивство. */
    private static final double HEART_MIN_REMAINING = 2.0;

    // --- Режим «Ідеал» (B1.7) ---
    /** Частка ПОТОЧНОЇ духовності жертви, що перетікає до злодія. */
    private static final double IDEAL_SPIRITUALITY_SHARE = 0.15;
    /** Скільки триває апатія (без спринту, без досвіду, слабкість). */
    public static final int IDEAL_APATHY_SECONDS = 60;

    // --- Розшифрування ілюзій, тір Посл. 5 (B2) ---
    /** Через скільки ворожа ілюзія на самому Крадії снів сама розсипається. */
    public static final int ILLUSION_DISPEL_SECONDS = 2;

    // --- Личина (A1) ---
    public static final int DISGUISE_COST = 140;
    public static final int DISGUISE_COOLDOWN_SECONDS = 600;
    public static final long DISGUISE_DURATION_MILLIS = 10L * 60_000L;
    /** Личина коштує глузду дорожче за звичайну крадіжку — це обряд, не рух руки. */
    public static final int DISGUISE_CORRUPTION = 3;
    /** Множник шкоди від «вкраденої відповіді на молитву», доки личина тримається. */
    public static final double DISGUISE_DAMAGE_MULTIPLIER = 1.25;
    /** Скільки триває викриття: провалений обряд лишає злодія без здібностей. */
    public static final int DISGUISE_EXPOSURE_SECONDS = 500;
    /** Скільки запалених свічок треба вівтарю Личини (як і в Ритуальній магії). */
    public static final int DISGUISE_CANDLES_REQUIRED = 3;

    private static final double DISGUISE_BASE_CHANCE = 0.90;
    private static final double DISGUISE_CHANCE_PER_ACCESS = 0.06;
    private static final double DISGUISE_MIN_CHANCE = 0.25;

    // --- Шанс успіху ---
    private static final double BASE_CHANCE = 0.50;
    private static final double CHANCE_PER_RECIPE = 0.03;
    private static final double MAX_RECIPE_BONUS = 0.25;
    private static final double CHANCE_PER_VICTIM_LEVEL = 0.08;
    private static final double MIN_CHANCE = 0.10;
    private static final double MAX_CHANCE = 0.95;

    /**
     * Шанс успішної концептуальної крадіжки — та сама форма, що в Прометея, але з базою
     * Посл. 5: знання шляху жертви піднімає, сильніша жертва опускає.
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

    /**
     * Скільки духовності справді перетікає в режимі «Ідеал».
     *
     * <p>Два затиски. Перший — беремо 15 % від ПОТОЧНОЇ духовності жертви, а не від
     * максимуму: висушена жертва не має бути кращою ціллю за повну. Другий — не більше,
     * ніж злодію бракує до власного максимуму: надлишок згорає, тож союзника не можна
     * доїти як батарейку.
     *
     * @param victimCurrentSpirituality поточна духовність жертви
     * @param thiefMissingSpirituality  скільки злодію бракує до максимуму
     * @return перенесена кількість, ніколи не від'ємна
     */
    public static int idealSpiritualityTransfer(int victimCurrentSpirituality,
                                                int thiefMissingSpirituality) {
        int taken = (int) Math.round(Math.max(0, victimCurrentSpirituality) * IDEAL_SPIRITUALITY_SHARE);
        return Math.min(taken, Math.max(0, thiefMissingSpirituality));
    }

    /**
     * Скільки максимального здоров'я справді переходить у режимі «Серце».
     *
     * <p>У слабкої жертви беруть менше: після крадіжки їй лишається хоча б одне серце,
     * інакше режим був би просто вбивством із затримкою.
     *
     * @param victimMaxHealth поточний максимум здоров'я жертви
     * @return перенесена кількість, ніколи не від'ємна
     */
    public static double heartTransfer(double victimMaxHealth) {
        return Math.max(0.0, Math.min(HEART_HEALTH_TRANSFER, victimMaxHealth - HEART_MIN_REMAINING));
    }

    /**
     * Шанс, що Личина витримає. Вікі: «the higher the intelligence of the corresponding True
     * Deity, the greater the probability for the Disguise to fail» — божеств у грі немає, тож
     * за «розум» править вага самої церкви: скільки шляхів вона тримає під собою.
     *
     * @param pathwayAccesses скільки шляхів у домені церкви ({@code Institution.accesses()})
     * @return шанс у межах [0.25, 0.90]
     */
    public static double disguiseSuccessChance(int pathwayAccesses) {
        double penalty = Math.max(0, pathwayAccesses) * DISGUISE_CHANCE_PER_ACCESS;
        return clamp(DISGUISE_BASE_CHANCE - penalty, DISGUISE_MIN_CHANCE, DISGUISE_BASE_CHANCE);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
