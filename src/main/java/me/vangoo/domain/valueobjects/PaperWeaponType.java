package me.vangoo.domain.valueobjects;

/**
 * Види зброї, яку Фокусник створює з паперу (Seq 7 — «Папір як зброя»,
 * еволюція Паперового різака). Вартість у папері 32…64 залежно від сили.
 *
 * <p>Чистий VO: лише числа й український підпис. Мапінг на {@code Material},
 * ефекти удару та {@code PotionEffectType} живуть у шарі pathways.
 */
public enum PaperWeaponType {

    /** Бита — сильний нокбек, помірний бонус-урон. */
    BAT("Паперова бита", 32, 3, 1.4, 0, 8),
    /** Тростина — сповільнює ціль при ударі. */
    CANE("Паперова тростина", 48, 4, 0.2, 100, 6),
    /** Цегла — важкий разовий урон. */
    BRICK("Паперова цегла", 64, 8, 0.4, 0, 4);

    private final String displayName;
    private final int paperCost;
    private final int bonusDamage;
    private final double knockback;
    private final int slowTicks;
    private final int uses;

    PaperWeaponType(String displayName, int paperCost, int bonusDamage,
                    double knockback, int slowTicks, int uses) {
        this.paperCost = paperCost;
        this.displayName = displayName;
        this.bonusDamage = bonusDamage;
        this.knockback = knockback;
        this.slowTicks = slowTicks;
        this.uses = uses;
    }

    public String displayName() {
        return displayName;
    }

    public int paperCost() {
        return paperCost;
    }

    public int bonusDamage() {
        return bonusDamage;
    }

    public double knockback() {
        return knockback;
    }

    public int slowTicks() {
        return slowTicks;
    }

    /** Скільки ударів витримує предмет, перш ніж розсипатись. */
    public int uses() {
        return uses;
    }
}
