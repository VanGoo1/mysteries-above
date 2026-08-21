package me.vangoo.domain.creatures;

/**
 * Чисте правило: чи можна показати цю істоту гравцеві з такою послідовністю.
 *
 * <p>Apex-істоти — це Послідовність 5. Вони ваншотять будь-кого слабшого, а їхній лут —
 * інгредієнти рецепта Посл. 5, тобто гравцеві Посл. 9 вони не потрібні ні як виклик, ні як
 * здобич. Тому apex зустрічає лише той, хто вже підійшов до Посл. 5 упритул.
 *
 * <p>Одне правило на всіх споживачів (структурний спавн, ambient-спавн) — щоб гейт не
 * лишився в одному лістенері, поки істота приходить із другого.
 */
public final class ApexGate {

    /** Гравець мусить бути щонайменше Посл. 6 (менше число = сильніший), щоб зустріти apex. */
    public static final int APEX_MIN_SEQUENCE = 6;

    private ApexGate() {}

    /**
     * @param tier                  тір істоти
     * @param playerSequenceLevel   послідовність гравця, або {@code null}, якщо він не потойбічний
     */
    public static boolean allows(CreatureTier tier, Integer playerSequenceLevel) {
        if (tier != CreatureTier.APEX) return true;
        return playerSequenceLevel != null && playerSequenceLevel <= APEX_MIN_SEQUENCE;
    }
}
