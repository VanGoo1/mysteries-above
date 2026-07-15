package me.vangoo.domain.organizations;

/** Ранги церкви за сумарним вкладом. Стеля замовлень: чим вищий ранг — тим сильніші зілля. */
public enum ChurchRank {
    VIRIANYN("Вірянин", 8),
    SLUZHKA("Служка", 6),
    DYAKON("Диякон", 4),
    YEPYSKOP("Єпископ", 2),
    KARDYNAL("Кардинал", 0);

    private final String displayName;
    private final int minOrderSequence;

    ChurchRank(String displayName, int minOrderSequence) {
        this.displayName = displayName;
        this.minOrderSequence = minOrderSequence;
    }

    public String displayName() {
        return displayName;
    }

    /** Найнижча (найсильніша) послідовність зілля, доступна рангу. */
    public int minOrderSequence() {
        return minOrderSequence;
    }

    /** @param thresholds мінімальний lifetime-вклад кожного рангу (5 значень, [0] = 0). */
    public static ChurchRank of(int lifetimeContribution, int[] thresholds) {
        if (thresholds.length != values().length) {
            throw new IllegalArgumentException("expected " + values().length + " thresholds");
        }
        ChurchRank result = VIRIANYN;
        for (ChurchRank rank : values()) {
            if (lifetimeContribution >= thresholds[rank.ordinal()]) {
                result = rank;
            }
        }
        return result;
    }

    /** Фактична стеля замовлення: обмежують і ранг, і PARTIAL-ліміт доступу шляху. */
    public int lowestOrderableSequence(PathwayAccess access) {
        return Math.max(minOrderSequence, access.minSequence());
    }
}
