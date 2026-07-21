package me.vangoo.domain.valueobjects;

/**
 * Види локальних ілюзій Фокусника (Seq 7 шляху Блазня — Illusion Creation).
 *
 * <p>Чистий VO: id + вартість духовності. Конкретні звуки/партикли/дим —
 * у шарі pathways ({@code IllusionCreation}).
 */
public enum IllusionKind {

    /** Фальшивий вибух — гучний звук + дим. */
    EXPLOSION("Ілюзія вибуху", 40),
    /** Фальшиві кроки — тихі звуки поблизу цілі. */
    FOOTSTEPS("Ілюзія кроків", 20),
    /** Фальшива пожежа — вогняні партикли + тріск. */
    FIRE("Ілюзія пожежі", 30);

    private final String displayName;
    private final int spiritualityCost;

    IllusionKind(String displayName, int spiritualityCost) {
        this.displayName = displayName;
        this.spiritualityCost = spiritualityCost;
    }

    public String displayName() {
        return displayName;
    }

    public int spiritualityCost() {
        return spiritualityCost;
    }
}
