package me.vangoo.domain.creatures;

import java.util.Collection;
import java.util.Optional;

/**
 * Чисте правило Закону Конвергенції (аналог CreatureSelector). Обирає, до якого резонансного
 * Beyonder'а тяжіє джерело, і з якою силою. Детерміноване, без Bukkit, без стану.
 *
 * <p>Резонанс: той самий шлях (сильний) або та сама PathwayGroup (сусід). Магніт обирається за
 * вагою {@code resonanceWeight × seqMultiplier / (1 + distance)} (найбільша вага; тайбрейк —
 * найближчий, далі — менший UUID). Сила нуджа {@code strength = resonanceWeight × seqMultiplier /
 * MAX_SCORE} ∈ (0,1] і НЕ залежить від відстані — потрібна есенція тяжіє однаково відчутно на
 * всьому радіусі.
 */
public final class ConvergencePull {

    private static final double SAME_PATHWAY_WEIGHT = 2.0;
    private static final double NEIGHBOR_WEIGHT = 1.0;
    private static final double NEXT_NEEDED_MULT = 4.0; // source.sequence == beyonder.seq - 1
    private static final double CURRENT_MULT = 2.0;     // source.sequence == beyonder.seq
    private static final double MAX_SCORE = SAME_PATHWAY_WEIGHT * NEXT_NEEDED_MULT; // 8.0

    public Optional<PullResult> computePull(ConvergenceSource source,
                                            Collection<ResonantBeyonder> beyonders,
                                            double radius) {
        if (source == null || beyonders == null || beyonders.isEmpty()) return Optional.empty();
        double r2 = radius * radius;

        ResonantBeyonder best = null;
        double bestWeight = -1.0;
        double bestDistSq = Double.MAX_VALUE;
        double bestScore = 0.0;

        for (ResonantBeyonder b : beyonders) {
            double resonance = resonanceWeight(source, b);
            if (resonance <= 0.0) continue;

            double dx = b.x() - source.x();
            double dz = b.z() - source.z();
            double distSq = dx * dx + dz * dz;
            if (distSq > r2) continue;

            double score = resonance * seqMultiplier(source, b);
            double weight = score / (1.0 + Math.sqrt(distSq));

            boolean better = weight > bestWeight
                    || (weight == bestWeight && distSq < bestDistSq)
                    || (weight == bestWeight && distSq == bestDistSq
                        && (best == null || b.id().compareTo(best.id()) < 0));
            if (better) {
                best = b;
                bestWeight = weight;
                bestDistSq = distSq;
                bestScore = score;
            }
        }

        if (best == null) return Optional.empty();
        return Optional.of(new PullResult(best.id(), bestScore / MAX_SCORE));
    }

    private double resonanceWeight(ConvergenceSource s, ResonantBeyonder b) {
        if (s.pathway() == null || b.pathway() == null) return 0.0;
        if (s.pathway().equalsIgnoreCase(b.pathway())) return SAME_PATHWAY_WEIGHT;
        if (s.group() != null && b.group() != null && s.group().equalsIgnoreCase(b.group())) {
            return NEIGHBOR_WEIGHT;
        }
        return 0.0;
    }

    private double seqMultiplier(ConvergenceSource s, ResonantBeyonder b) {
        if (s.sequence() == b.sequenceLevel() - 1) return NEXT_NEEDED_MULT;
        if (s.sequence() == b.sequenceLevel()) return CURRENT_MULT;
        return 1.0;
    }
}
