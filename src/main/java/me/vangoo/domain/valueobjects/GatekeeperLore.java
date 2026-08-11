package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 5 шляху Смерті (Воротар).
 *
 * <p>Посл. 5 — перша для цього тіру; числа слабших тірів живуть у {@link SpiritGuideLore},
 * {@link SpiritMediumLore}, {@link GravediggerLore}, {@link CorpseCollectorLore}. Клас
 * наповнюється по мірі мілстоунів плану — числа й {@link #growth(Sequence, ScalingStrategy)}
 * додаються разом із першою здібністю, якій вони справді потрібні (на Посл. 5 множник 1.0).
 */
public final class GatekeeperLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 5;

    private GatekeeperLore() {
    }

    // --- Тіло воротаря (PhysicalEnhancement, спільний identity "death_physique") ---
    public static final int PHYSIQUE_HP_BASE = 10;

    // --- Крок Воротаря (ривок уперед) ---
    public static final int STEP_COST = 20;
    private static final int STEP_COOLDOWN_SECONDS = 8;
    private static final int STEP_COOLDOWN_FLOOR_SECONDS = 4;
    private static final double STEP_DISTANCE = 8.0;
    private static final double STEP_DISTANCE_CAP = 12.0;
    private static final int STEP_IMPACT_DAMAGE = 5;

    /** Кулдаун Кроку (сек): Посл. 5 = 8, коротшає WEAK, не нижче підлоги. */
    public static int stepCooldownSeconds(Sequence sequence) {
        double seconds = STEP_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(STEP_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /** Дальність ривка (блоки): Посл. 5 = 8, росте WEAK до стелі 12. */
    public static double stepDistance(Sequence sequence) {
        return Math.min(STEP_DISTANCE_CAP, STEP_DISTANCE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Шкода при приземленні: Посл. 5 = 5, росте MODERATE. */
    public static int stepImpactDamage(Sequence sequence) {
        return (int) Math.round(STEP_IMPACT_DAMAGE * growth(sequence, ScalingStrategy.MODERATE));
    }

    // --- Двері в Загробний Світ (спільні ціна й кулдаун на всі 4 режими) ---
    // Ability.getSpiritualityCost()/getCooldown(Sequence) не бачать ні кастера, ні поточного
    // режиму — фреймворк фізично не годен дати чотирьом режимам чотири різні ціни/кулдауни
    // без ручного дублювання AbilityResourceConsumer. Той самий компроміс уже прийнятий
    // Вердиктом (Justiciar, Посл. 6): один каст = одна ціна незалежно від обраного режиму.
    // Числа беремо від DRAG — найважчого й першого реалізованого режиму.
    public static final int DOOR_COST = 200;
    private static final int DOOR_COOLDOWN_SECONDS = 40;
    private static final int DOOR_COOLDOWN_FLOOR_SECONDS = 10;

    // --- Дальність, на яку можна поставити двері (дивлячись у точку) — спільна для всіх
    // чотирьох режимів, не лише DRAG: жоден режим більше не бере ціль-сутність, кожен
    // натомість ставить браму в точці, куди дивиться гравець. ---
    private static final double DOOR_PLACEMENT_RANGE = 20.0;
    private static final double DOOR_PLACEMENT_RANGE_CAP = 32.0;

    // --- DRAG: радіус всмоктування біля брами, шкода, поріг страти ---
    private static final double DRAG_GATE_AOE_RADIUS = 8.0;
    private static final double DRAG_GATE_AOE_RADIUS_CAP = 14.0;
    private static final int DRAG_DAMAGE = 8;
    /** Поріг страти — фіксований, не росте від Послідовності (design decision #6 плану). */
    public static final double DRAG_EXECUTE_HP_FRACTION = 0.25;
    /** Тління на цілі (сек) — темп ефекту, не сила, тому стала. */
    public static final int DRAG_WITHER_SECONDS = 4;
    /** Повільність біля брами (сек) — та сама причина сталості. */
    public static final int DRAG_SLOWNESS_SECONDS = 3;

    /** Кулдаун Дверей (сек): Посл. 5 = 60, коротшає WEAK, не нижче підлоги. */
    public static int doorCooldownSeconds(Sequence sequence) {
        double seconds = DOOR_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(DOOR_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    /** Дальність розміщення брами (блоки): Посл. 5 = 20, росте WEAK до стелі 32. */
    public static double doorPlacementRange(Sequence sequence) {
        return Math.min(DOOR_PLACEMENT_RANGE_CAP, DOOR_PLACEMENT_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Радіус всмоктування довкола брами (блоки): Посл. 5 = 8, росте WEAK до стелі 14. */
    public static double dragGateAoeRadius(Sequence sequence) {
        return Math.min(DRAG_GATE_AOE_RADIUS_CAP, DRAG_GATE_AOE_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Шкода DRAG по головній цілі: Посл. 5 = 8, росте MODERATE. */
    public static int dragDamage(Sequence sequence) {
        return (int) Math.round(DRAG_DAMAGE * growth(sequence, ScalingStrategy.MODERATE));
    }

    // --- PURGE: радіус очищення хмар/ефектів/вогню довкола кастера ---
    private static final double PURGE_RADIUS = 10.0;
    private static final double PURGE_RADIUS_CAP = 18.0;

    /** Радіус Очищення (блоки): Посл. 5 = 10, росте WEAK до стелі 18. */
    public static double purgeRadius(Sequence sequence) {
        return Math.min(PURGE_RADIUS_CAP, PURGE_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    // --- BIND: радіус скутих рук довкола кастера, тривалість і сила ефектів (фіксовані) ---
    private static final double BIND_RADIUS = 6.0;
    private static final double BIND_RADIUS_CAP = 11.0;
    /** Темп контролю, не сила — фіксовано, як DRAG_SLOWNESS_SECONDS. */
    public static final int BIND_DURATION_SECONDS = 4;
    /** Рівень-індекс: 4 = Повільність V. */
    public static final int BIND_SLOWNESS_AMPLIFIER = 4;

    /** Радіус Скутих рук (блоки): Посл. 5 = 6, росте WEAK до стелі 11. */
    public static double bindRadius(Sequence sequence) {
        return Math.min(BIND_RADIUS_CAP, BIND_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    // --- DESCEND: радіус/тривалість зони ростуть; надбавка нежиті й лікування — фіксовані числа
    // самого дизайну (design table), не залежать від Послідовності ---
    private static final double DESCEND_RADIUS = 12.0;
    private static final double DESCEND_RADIUS_CAP = 20.0;
    private static final int DESCEND_DURATION_SECONDS = 12;
    private static final int DESCEND_DURATION_CAP_SECONDS = 20;
    /** Надбавка до удару власної нежиті всередині зони: «+20%». */
    public static final double DESCEND_UNDEAD_DAMAGE_BONUS = 0.20;
    /** Лікування власника за секунду, поки він у зоні: «1 HP/s». */
    public static final double DESCEND_OWNER_HEAL_PER_SECOND = 1.0;

    /** Радіус зони Сходження (блоки): Посл. 5 = 12, росте WEAK до стелі 20. */
    public static double descendRadius(Sequence sequence) {
        return Math.min(DESCEND_RADIUS_CAP, DESCEND_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Тривалість зони Сходження (сек): Посл. 5 = 12, росте MODERATE до стелі 20. */
    public static int descendDurationSeconds(Sequence sequence) {
        return (int) Math.min(DESCEND_DURATION_CAP_SECONDS,
                Math.round(DESCEND_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)));
    }

    // --- Внутрішній Загробний Світ (A9–A13): доплата за окупанта — база-найдешевший +
    // різниця вручну, той самий прийом, що SpiritPact.pay(); кулдаун касту спільний на всіх
    // шістьох, як і в SpiritPact.pactCooldownSeconds. Поглинання/виселення — безкоштовні й
    // без кулдауну: єдиний слот окупанта сам собою не дає їх спамити. ---
    public static final int UNDERWORLD_CAST_BASE_COST = 300;
    private static final int UNDERWORLD_CAST_COOLDOWN_SECONDS = 100;
    private static final int UNDERWORLD_CAST_COOLDOWN_FLOOR_SECONDS = 30;

    public static final int DEATH_KNIGHT_CAST_COST = 40;
    public static final int SHADOW_PYTHON_CAST_COST = 45;
    public static final int LIVING_SHADOW_CAST_COST = 35;
    public static final int LAKE_GODDESS_CAST_COST = 50;
    public static final int WANDERING_SPIRIT_CAST_COST = 30;
    public static final int RESURRECTED_SERVANT_CAST_COST = 30;

    /** −2…−8 max HP залежно від сили окупанта (design table); від Послідовності не залежить. */
    public static final double DEATH_KNIGHT_EROSION_HP = 5;
    public static final double SHADOW_PYTHON_EROSION_HP = 5;
    public static final double LIVING_SHADOW_EROSION_HP = 3;
    public static final double LAKE_GODDESS_EROSION_HP = 8;
    public static final double WANDERING_SPIRIT_EROSION_HP = 2;
    public static final double RESURRECTED_SERVANT_EROSION_HP = 3;

    /** Дренаж розсудку, поки окупант живе всередині: 1 щоразу за цей період. */
    public static final int UNDERWORLD_SANITY_DRAIN_PERIOD_SECONDS = 15;
    public static final int UNDERWORLD_SANITY_DRAIN_AMOUNT = 1;

    /** Кулдаун касту сили окупанта (сек): Посл. 5 = 30, коротшає WEAK, не нижче підлоги. */
    public static int underworldCastCooldownSeconds(Sequence sequence) {
        double seconds = UNDERWORLD_CAST_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(UNDERWORLD_CAST_COOLDOWN_FLOOR_SECONDS, Math.round(seconds));
    }

    // --- Четверо нових духів Світу Духів (A10/A11/S2/S3) — ціна ДОМОВЛЕНОСТІ (SpiritPact,
    // почет), окрема від ціни КАСТУ окупантом вище. Посланець і Морська Примара — бойові
    // активи (дорожче за DEATH_KNIGHT_COST=70); Гній і Бліда Дівчинка — пасивні, дешевші за
    // LIVING_SHADOW_COST=50 (SpiritGuideLore). Точних чисел вікі не дає — інтерпретація,
    // як і A8 у плані Дверей. ---
    public static final int DEATH_ENVOY_RECRUIT_COST = 90;
    public static final int PUS_OF_MAN_RECRUIT_COST = 60;
    public static final int PALE_GIRL_RECRUIT_COST = 60;
    public static final int SEA_BEASTS_RECRUIT_COST = 85;

    // --- Ті самі четверо як окупанти ВЗС: castCost 0 = пасивний (діє сама сесія,
    // performExecution на каст не заходить), інакше — з design table плану (60/55). ---
    public static final int DEATH_ENVOY_CAST_COST = 60;
    public static final int SEA_BEASTS_CAST_COST = 55;
    public static final int PUS_OF_MAN_CAST_COST = 0;
    public static final int PALE_GIRL_CAST_COST = 0;

    public static final double DEATH_ENVOY_EROSION_HP = 7;
    public static final double SEA_BEASTS_EROSION_HP = 6;
    public static final double PUS_OF_MAN_EROSION_HP = 4;
    public static final double PALE_GIRL_EROSION_HP = 4;

    // --- Гній Людини (A11): рятує від смертельного удару раз на 5 хв. ---
    public static final int PUS_OF_MAN_WARD_COOLDOWN_SECONDS = 5 * 60;
    public static final int PUS_OF_MAN_INVISIBILITY_SECONDS = 5;
    public static final int PUS_OF_MAN_WEAKNESS_SECONDS = 10;

    // --- S1: посилене Прикликання духів — стеля почту 10 → 12 («slightly increased, but it
    // doesn't change much»). Місце методу — тут, а не в SpiritGuideLore: перехід стається САМЕ
    // на Посл. 5, і GatekeeperLore уже дивиться на слабші тіри як на свою базу (як в інших
    // методів цього класу), тоді як зворотна залежність (SpiritGuideLore → GatekeeperLore)
    // була б рухом проти шару тірів. SpiritGuideLore.RETINUE_CAP лишається як є — це база
    // «для Посл. 6 і слабших», яку тут же й читають. ---
    public static final int RETINUE_CAP = 12;

    /** Стеля почту (слуг): 10 для Посл. 6+ ({@link SpiritGuideLore#RETINUE_CAP}), 12 від Посл. 5. */
    public static int retinueCap(Sequence sequence) {
        return sequence.level() <= BASE_SEQUENCE ? RETINUE_CAP : SpiritGuideLore.RETINUE_CAP;
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 5 (там рівно 1.0). */
    private static double growth(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(sequence.level(), strategy)
                / SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy);
    }
}
