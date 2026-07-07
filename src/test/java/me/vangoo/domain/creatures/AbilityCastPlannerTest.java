package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityCastPlannerTest {

    // kit: signature (пріоритет 1, кулдаун 300), filler (пріоритет 2, кулдаун 200); ГКД 120
    private static AbilityCastPlanner planner() {
        return new AbilityCastPlanner(List.of(
                new AbilityCastPlanner.KitEntry("signature", 300),
                new AbilityCastPlanner.KitEntry("filler", 200)
        ), 120);
    }

    @Test
    void picksHighestPriorityWhenAllReady() {
        assertEquals(Optional.of("signature"), planner().pickNext(0));
    }

    @Test
    void gcdBlocksAnyCastUntilElapsed() {
        var p = planner();
        p.pickNext(0);
        assertEquals(Optional.empty(), p.pickNext(119));
    }

    @Test
    void fallsBackToLowerPriorityWhileSignatureOnItsOwnCooldown() {
        var p = planner();
        p.pickNext(0); // signature: готова знову з t=300
        assertEquals(Optional.of("filler"), p.pickNext(120));
    }

    @Test
    void returnsEmptyWhenEverythingOnCooldown() {
        var p = planner();
        p.pickNext(0);   // signature до 300
        p.pickNext(120); // filler до 320; ГКД до 240
        assertEquals(Optional.empty(), p.pickNext(240)); // ГКД минув, але обидві на кулдауні
    }

    @Test
    void signatureRegainsPriorityAfterItsCooldown() {
        var p = planner();
        p.pickNext(0);
        p.pickNext(120);
        assertEquals(Optional.of("signature"), p.pickNext(300));
    }

    @Test
    void skillCooldownsAreIndependentPerPlannerInstance() {
        var first = planner();
        first.pickNext(0);
        assertEquals(Optional.of("signature"), planner().pickNext(0));
    }
}
