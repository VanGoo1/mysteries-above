package me.vangoo.domain.rituals;

/** Оцінка жертви: скільки духовності повертає спалення предмета на вівтарі. */
public final class SacrificeAppraiser {

    public static int spiritualityFor(SacrificeKind kind) {
        return switch (kind) {
            case PATHWAY_INGREDIENT -> 300;
            case PRECIOUS -> 150;
            case VALUABLE -> 60;
            case TRIFLE -> 10;
        };
    }

    private SacrificeAppraiser() {
    }
}
