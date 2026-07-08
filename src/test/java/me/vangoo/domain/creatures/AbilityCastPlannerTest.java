package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityCastPlannerTest {

    // kit: signature (вага 2), filler (вага 1); обидві з кулдауном 300; ГКД 120
    private static AbilityCastPlanner planner(long seed) {
        return new AbilityCastPlanner(List.of(
                new AbilityCastPlanner.KitEntry("signature", 300),
                new AbilityCastPlanner.KitEntry("filler", 300)
        ), 120, new Random(seed));
    }

    @Test
    void gcdBlocksAnyCastUntilElapsed() {
        var p = planner(1);
        assertTrue(p.pickNext(0).isPresent());
        assertEquals(Optional.empty(), p.pickNext(119));
    }

    @Test
    void neverPicksAbilityOnItsOwnCooldown() {
        var p = planner(1);
        String first = p.pickNext(0).orElseThrow();
        String second = p.pickNext(120).orElseThrow(); // перша ще на кулдауні до 300
        assertNotEquals(first, second);
    }

    @Test
    void returnsEmptyWhenEverythingOnCooldown() {
        var p = planner(1);
        p.pickNext(0);   // готова знову з 300
        p.pickNext(120); // готова знову з 420
        assertEquals(Optional.empty(), p.pickNext(240)); // ГКД минув, обидві на кулдауні
    }

    @Test
    void abilityBecomesAvailableAgainAfterItsCooldown() {
        var p = planner(1);
        String first = p.pickNext(0).orElseThrow();
        p.pickNext(120);
        // t=300: перша знову готова, друга — лише з 420 → вибір детермінований
        assertEquals(first, p.pickNext(300).orElseThrow());
    }

    @Test
    void selectionIsWeightedTowardEarlierEntries() {
        // обидві завжди готові (кулдаун 0), ГКД 1 → чиста перевірка ваг 2:1
        var p = new AbilityCastPlanner(List.of(
                new AbilityCastPlanner.KitEntry("signature", 0),
                new AbilityCastPlanner.KitEntry("filler", 0)
        ), 1, new Random(42));
        int signaturePicks = 0;
        for (long t = 0; t < 3000; t++) {
            if (p.pickNext(t * 2).orElseThrow().equals("signature")) signaturePicks++;
        }
        // очікування 2/3 від 3000 = 2000; фіксований сід робить перевірку детермінованою
        assertTrue(signaturePicks > 1700 && signaturePicks < 2300,
                "signature picked " + signaturePicks + "/3000 — вага 2:1 не працює");
    }

    @Test
    void skillCooldownsAreIndependentPerPlannerInstance() {
        planner(1).pickNext(0);
        assertTrue(planner(1).pickNext(0).isPresent());
    }
}
