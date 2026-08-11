package me.vangoo.domain.valueobjects;

import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;

/**
 * Балансні числа Послідовності 7 шляху Смерті (Медіум Духів).
 *
 * <p>Одне джерело правди для тіру: Сприйняття духів (пасивка), Прикликання духів (Ілюзорне око,
 * Морозна тінь, Земляний дух), Голос мертвих (дві гілки допиту) і Трупна личина. Чиста
 * математика — без Bukkit; ефекти живуть у {@code pathways.death.abilities}.
 *
 * <p>Посл. 7 — перша для цього тіру, тож усі базові числа задані саме для неї, а
 * {@link #growth(Sequence, ScalingStrategy)} лише піднімає їх для сильніших Послідовностей
 * (на Посл. 7 множник рівно 1.0). Числа слабших тірів живуть окремо, у
 * {@link GravediggerLore} і {@link CorpseCollectorLore}.
 */
public final class SpiritMediumLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private static final int BASE_SEQUENCE = 7;

    private SpiritMediumLore() {
    }

    // --- Тіло медіума (PhysicalEnhancement, спільний identity "death_physique") ---
    /** База бонусного HP. Свідомо мала надбавка понад Посл. 8 (6): вікі каже «not significant». */
    public static final int PHYSIQUE_HP_BASE = 7;

    // --- Сприйняття духів (постійна пасивка, безкоштовна) ---
    private static final double PERCEPTION_RADIUS = 24.0;
    private static final double PERCEPTION_RADIUS_CAP = 48.0;

    // --- Голос мертвих: душа, що затрималась на місці смерті ---
    /**
     * Скільки душа висить на місці смерті (сек). Від Послідовності НЕ залежить: це строк самої
     * душі, а не сила медіума. Не персиститься — рестарт її стирає (див. план, Part 9).
     */
    public static final int SOUL_TTL_SECONDS = 30 * 60;

    // --- Прикликання духів: спільна ціна не задається, кожен дух має свою ---

    // --- Ілюзорне око (розвідка) ---
    public static final int ILLUSORY_EYE_COST = 30;
    private static final int ILLUSORY_EYE_COOLDOWN_SECONDS = 40;
    private static final int ILLUSORY_EYE_COOLDOWN_FLOOR_SECONDS = 15;
    private static final int ILLUSORY_EYE_DURATION_SECONDS = 15;
    private static final double ILLUSORY_EYE_RADIUS = 48.0;
    private static final double ILLUSORY_EYE_RADIUS_CAP = 64.0;

    // --- Морозна тінь (домен холоду + крижаний обладунок + коса) ---
    public static final int FROST_SHADOW_COST = 90;
    private static final int FROST_COOLDOWN_SECONDS = 50;
    private static final int FROST_COOLDOWN_FLOOR_SECONDS = 30;
    private static final int FROST_DURATION_SECONDS = 12;
    /** Радіус домену росте MODERATE саме щоб дійти до вікіних «10 to 20 meter range». */
    private static final double FROST_RADIUS = 10.0;
    private static final double FROST_RADIUS_CAP = 20.0;
    private static final double FROST_TICK_DAMAGE = 1.0;
    private static final double FROST_TICK_DAMAGE_CAP = 3.0;
    /** Надбавка до урону морозної коси понад 1.0: Посл. 7 = +40%. */
    private static final double SCYTHE_DAMAGE_BONUS = 0.4;
    private static final double SCYTHE_DAMAGE_MULTIPLIER_CAP = 2.5;

    // --- Земляний дух (затягування в землю; коштує крові) ---
    public static final int EARTH_SPIRIT_COST = 80;
    /** Ціна кров'ю в HP (2 = серце). Стала: кров — це кров, сильнішому медіуму її не менше. */
    public static final int EARTH_SPIRIT_BLOOD_HP = 4;
    private static final int EARTH_COOLDOWN_SECONDS = 55;
    private static final int EARTH_COOLDOWN_FLOOR_SECONDS = 20;
    private static final int EARTH_HOLD_SECONDS = 5;
    /** Радіус болота навколо схопленого (блоки): сусіди грузнуть, але в землю не йдуть. */
    public static final double EARTH_SWAMP_RADIUS = 4.0;
    private static final double EARTH_RANGE = 12.0;
    private static final double EARTH_RANGE_CAP = 24.0;

    // --- Голос мертвих ---
    public static final int VOICE_COST = 100;
    private static final int VOICE_DEAD_COOLDOWN_SECONDS = 500;
    private static final int VOICE_DEAD_COOLDOWN_FLOOR_SECONDS = 40;
    private static final double VOICE_DEAD_RADIUS = 8.0;
    private static final double VOICE_DEAD_RADIUS_CAP = 16.0;
    private static final int VOICE_LIVING_COOLDOWN_SECONDS = 60;
    private static final int VOICE_LIVING_COOLDOWN_FLOOR_SECONDS = 20;
    private static final double VOICE_LIVING_RANGE = 16.0;
    private static final double VOICE_LIVING_RANGE_CAP = 32.0;
    /** Розсудок за кожну Послідовність переваги цілі; сильніша воля відбиває допит боляче. */
    private static final int VOICE_BACKLASH_PER_LEVEL = 8;
    private static final int VOICE_BACKLASH_CAP = 40;

    // --- Трупна личина (тогл) ---
    public static final int GUISE_ACTIVATION_COST = 10;
    /** Періодична ціна тогла за секунду; від Послідовності не залежить. */
    public static final int GUISE_PERIODIC_COST = 10;
    /** Антиспам перемикача, не сила — тому стала. */
    public static final int GUISE_COOLDOWN_SECONDS = 5;

    /** Радіус Сприйняття духів (блоки): Посл. 7 = 24, росте WEAK до стелі 48. */
    public static double perceptionRadius(Sequence sequence) {
        return Math.min(PERCEPTION_RADIUS_CAP,
                PERCEPTION_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Кулдаун Ілюзорного ока (сек): Посл. 7 = 45, коротшає WEAK, не нижче підлоги. */
    public static int illusoryEyeCooldownSeconds(Sequence sequence) {
        return shrink(ILLUSORY_EYE_COOLDOWN_SECONDS, ILLUSORY_EYE_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Скільки око світить цілі (сек): Посл. 7 = 15, росте MODERATE. */
    public static int illusoryEyeDurationSeconds(Sequence sequence) {
        return (int) Math.round(ILLUSORY_EYE_DURATION_SECONDS
                * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Радіус, у якому око підсвічує живе (блоки): Посл. 7 = 48, росте WEAK до стелі 64. */
    public static double illusoryEyeRadius(Sequence sequence) {
        return Math.min(ILLUSORY_EYE_RADIUS_CAP,
                ILLUSORY_EYE_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Кулдаун Морозної тіні (сек): Посл. 7 = 90, коротшає WEAK, не нижче підлоги. */
    public static int frostCooldownSeconds(Sequence sequence) {
        return shrink(FROST_COOLDOWN_SECONDS, FROST_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Скільки домен тримається (сек): Посл. 7 = 12, росте MODERATE. */
    public static int frostDurationSeconds(Sequence sequence) {
        return (int) Math.round(FROST_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Радіус мороженого домену (блоки): Посл. 7 = 10, росте MODERATE до вікіної стелі 20. */
    public static double frostRadius(Sequence sequence) {
        return Math.min(FROST_RADIUS_CAP, FROST_RADIUS * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Урон холоду за секунду в домені: Посл. 7 = 1.0 (пів серця), росте WEAK до стелі 3.0. */
    public static double frostTickDamage(Sequence sequence) {
        return Math.min(FROST_TICK_DAMAGE_CAP,
                FROST_TICK_DAMAGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Множник ближнього удару морозною косою: Посл. 7 = 1.4, надбавка росте WEAK до стелі 2.5. */
    public static double scytheDamageMultiplier(Sequence sequence) {
        double bonus = SCYTHE_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK);
        return Math.min(SCYTHE_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus);
    }

    /** Кулдаун Земляного духа (сек): Посл. 7 = 60, коротшає WEAK, не нижче підлоги. */
    public static int earthCooldownSeconds(Sequence sequence) {
        return shrink(EARTH_COOLDOWN_SECONDS, EARTH_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Скільки ціль тримається в землі (сек): Посл. 7 = 5, росте MODERATE. */
    public static int earthHoldSeconds(Sequence sequence) {
        return (int) Math.round(EARTH_HOLD_SECONDS * growth(sequence, ScalingStrategy.MODERATE));
    }

    /** Дальність прицілу Земляного духа (блоки): Посл. 7 = 12, росте WEAK до стелі 24. */
    public static double earthRange(Sequence sequence) {
        return Math.min(EARTH_RANGE_CAP, EARTH_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Кулдаун допиту мертвого (сек): Посл. 7 = 120, коротшає WEAK, не нижче підлоги. */
    public static int voiceDeadCooldownSeconds(Sequence sequence) {
        return shrink(VOICE_DEAD_COOLDOWN_SECONDS, VOICE_DEAD_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Радіус пошуку душі навколо медіума (блоки): Посл. 7 = 8, росте WEAK до стелі 16. */
    public static double voiceDeadRadius(Sequence sequence) {
        return Math.min(VOICE_DEAD_RADIUS_CAP,
                VOICE_DEAD_RADIUS * growth(sequence, ScalingStrategy.WEAK));
    }

    /** Кулдаун допиту живого (сек): Посл. 7 = 60, коротшає WEAK, не нижче підлоги. */
    public static int voiceLivingCooldownSeconds(Sequence sequence) {
        return shrink(VOICE_LIVING_COOLDOWN_SECONDS, VOICE_LIVING_COOLDOWN_FLOOR_SECONDS, sequence);
    }

    /** Дальність допиту живого (блоки): Посл. 7 = 16, росте WEAK до стелі 32. */
    public static double voiceLivingRange(Sequence sequence) {
        return Math.min(VOICE_LIVING_RANGE_CAP,
                VOICE_LIVING_RANGE * growth(sequence, ScalingStrategy.WEAK));
    }

    /**
     * Втрата розсудку, коли живу ціль допитати не вдалось: 8 за кожну Послідовність, на яку
     * ціль сильніша за медіума, до стелі 40. Рівна чи слабша ціль відкату не дає (0).
     */
    public static int voiceBacklashSanity(Sequence caster, Sequence target) {
        int levelsStronger = caster.level() - target.level();
        if (levelsStronger <= 0) {
            return 0;
        }
        return Math.min(VOICE_BACKLASH_CAP, VOICE_BACKLASH_PER_LEVEL * levelsStronger);
    }

    /** Кулдаун, що коротшає WEAK від бази до підлоги. */
    private static int shrink(int baseSeconds, int floorSeconds, Sequence sequence) {
        double seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK);
        return (int) Math.max(floorSeconds, Math.round(seconds));
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 7 (там рівно 1.0). */
    private static double growth(Sequence sequence, ScalingStrategy strategy) {
        return SequenceScaler.calculateMultiplier(sequence.level(), strategy)
                / SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy);
    }
}
