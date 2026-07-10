package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvergencePullTest {

    private final ConvergencePull pull = new ConvergencePull();

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void samePathwayBeatsNeighborAtEqualDistance() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder same = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 9, 10, 0);
        ResonantBeyonder neighbor = new ResonantBeyonder(B, "Door", "LordOfMysteries", 9, 0, 10);
        Optional<PullResult> res = pull.computePull(src, List.of(same, neighbor), 128);
        assertTrue(res.isPresent());
        assertEquals(A, res.get().targetId());
    }

    @Test
    void nextNeededSequenceGivesStrongerStrengthThanOther() {
        // Beyonder at seq 8 needs seq 7 next.
        ConvergenceSource needed = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ConvergenceSource other = new ConvergenceSource("Fool", "LordOfMysteries", 3, 0, 0);
        ResonantBeyonder b = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 8, 5, 0);
        double sNeeded = pull.computePull(needed, List.of(b), 128).orElseThrow().strength();
        double sOther = pull.computePull(other, List.of(b), 128).orElseThrow().strength();
        assertTrue(sNeeded > sOther);
    }

    @Test
    void outOfRadiusExcluded() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder far = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 9, 200, 0);
        assertTrue(pull.computePull(src, List.of(far), 128).isEmpty());
    }

    @Test
    void noResonanceReturnsEmpty() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder foreign = new ResonantBeyonder(A, "Visionary", "GodAlmighty", 9, 5, 0);
        assertTrue(pull.computePull(src, List.of(foreign), 128).isEmpty());
    }

    @Test
    void neighborResonatesForeignGroupDoesNot() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder neighbor = new ResonantBeyonder(A, "Error", "LordOfMysteries", 9, 5, 0);
        ResonantBeyonder foreign = new ResonantBeyonder(B, "Justiciar", "TheAnarchy", 9, 1, 0);
        Optional<PullResult> res = pull.computePull(src, List.of(neighbor, foreign), 128);
        assertTrue(res.isPresent());
        assertEquals(A, res.get().targetId());
    }

    @Test
    void nearestWinsAmongEqualResonance() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 9, 0, 0);
        ResonantBeyonder near = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 9, 3, 0);
        ResonantBeyonder far = new ResonantBeyonder(B, "Fool", "LordOfMysteries", 9, 20, 0);
        assertEquals(A, pull.computePull(src, List.of(far, near), 128).orElseThrow().targetId());
    }

    @Test
    void maxStrengthIsOneForSamePathwayNextNeeded() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder b = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 8, 5, 0);
        double strength = pull.computePull(src, List.of(b), 128).orElseThrow().strength();
        assertEquals(1.0, strength, 1e-9);
        assertTrue(strength > 0.0 && strength <= 1.0);
    }

    @Test
    void emptyCandidatesReturnsEmpty() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        assertTrue(pull.computePull(src, List.of(), 128).isEmpty());
    }
}
