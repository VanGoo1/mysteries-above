package me.vangoo.domain.organizations;

/**
 * Доступ інституції до шляху. {@code minSequence} — найнижча (найсильніша) послідовність,
 * яку інституція підтримує: 0 = повний доступ, N = «неповний до N-ї послідовності».
 * {@code branch} — опційна помітка підвладної групи (для Церкви Блазня), інакше "".
 */
public record PathwayAccess(String pathwayName, int minSequence, String branch) {

    public PathwayAccess {
        if (pathwayName == null || pathwayName.isBlank()) {
            throw new IllegalArgumentException("pathwayName must not be blank");
        }
        if (minSequence < 0 || minSequence > 9) {
            throw new IllegalArgumentException("minSequence must be in [0..9]: " + minSequence);
        }
        branch = branch == null ? "" : branch;
    }

    public static PathwayAccess full(String pathwayName) {
        return new PathwayAccess(pathwayName, 0, "");
    }

    public static PathwayAccess full(String pathwayName, String branch) {
        return new PathwayAccess(pathwayName, 0, branch);
    }

    public static PathwayAccess partial(String pathwayName, int minSequence) {
        return new PathwayAccess(pathwayName, minSequence, "");
    }

    public static PathwayAccess partial(String pathwayName, int minSequence, String branch) {
        return new PathwayAccess(pathwayName, minSequence, branch);
    }

    public boolean isFull() {
        return minSequence == 0;
    }

    /** Чи підтримує інституція цю послідовність шляху (9 = найслабша … 0 = найсильніша). */
    public boolean supportsSequence(int sequence) {
        return sequence >= minSequence;
    }
}
