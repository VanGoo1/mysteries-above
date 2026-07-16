package me.vangoo.domain.organizations;

/**
 * Ранг у таємній організації виводиться з ПОСЛІДОВНОСТІ гравця (сила = вага),
 * а не з вкладу — очок у орденів немає взагалі. Нуль власного стану.
 */
public enum OrderRank {
    PAWN("Пішак", 8),
    BLADE("Вістря", 6),
    TRUSTED("Довірений", 4),
    MAGISTER("Магістр", 2),
    HIDDEN_LORD("Прихований Владика", 0);

    private final String displayName;
    private final int minSequence; // найсильніша послідовність щабля (нижня межа діапазону)

    OrderRank(String displayName, int minSequence) {
        this.displayName = displayName;
        this.minSequence = minSequence;
    }

    public String displayName() {
        return displayName;
    }

    public static OrderRank of(int sequenceLevel) {
        int seq = Math.max(0, Math.min(9, sequenceLevel));
        for (OrderRank rank : values()) {
            if (seq >= rank.minSequence) {
                return rank;
            }
        }
        return HIDDEN_LORD;
    }

    public boolean atLeast(OrderRank other) {
        return ordinal() >= other.ordinal();
    }
}
