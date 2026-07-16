package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class InvitationRulesTest {

    private final Institution bloodSect = new Institution("order-blood-sanctify",
            InstitutionType.SECRET_ORDER, "Секта Освячення Крові", "лор", List.of());
    private final Institution doorOrder = new Institution("order-aurora",
            InstitutionType.SECRET_ORDER, "Орден Аврори", "лор",
            List.of(PathwayAccess.full("Door")));
    private final Institution wtOrder = new Institution("order-psychology-alchemists",
            InstitutionType.SECRET_ORDER, "Психологічні Алхіміки", "лор",
            List.of(PathwayAccess.full("Visionary")));
    private final Map<String, String> groups = Map.of(
            "Door", "LordOfMysteries", "Visionary", "GoddessOfOrigin");

    @Test
    void pathwaylessPlayerNeverInvited() {
        Optional<Institution> pick = InvitationRules.pickOrder(
                InvitationRules.DeedType.APEX_KILL, "Door", null,
                List.of(bloodSect, doorOrder), groups, new Random(1));
        assertTrue(pick.isEmpty());
    }

    @Test
    void apexKillPrefersOrderOfCreatureGroup() {
        for (int seed = 0; seed < 20; seed++) {
            Optional<Institution> pick = InvitationRules.pickOrder(
                    InvitationRules.DeedType.APEX_KILL, "Door", "Door",
                    List.of(bloodSect, doorOrder, wtOrder), groups, new Random(seed));
            assertEquals("order-aurora", pick.orElseThrow().id());
        }
    }

    @Test
    void beyonderKillsPreferPinnedBloodOrders() {
        for (int seed = 0; seed < 20; seed++) {
            Optional<Institution> pick = InvitationRules.pickOrder(
                    InvitationRules.DeedType.BEYONDER_KILLS, null, "Door",
                    List.of(doorOrder, bloodSect), groups, new Random(seed));
            assertEquals("order-blood-sanctify", pick.orElseThrow().id());
        }
    }

    @Test
    void candidatesMustAcceptPlayerPathway() {
        Optional<Institution> pick = InvitationRules.pickOrder(
                InvitationRules.DeedType.RAMPAGER_STOPPED, null, "Door",
                List.of(wtOrder), groups, new Random(1)); // wtOrder не приймає Door
        assertTrue(pick.isEmpty());
    }

    @Test
    void fallsBackToAnyAcceptingOrder() {
        Optional<Institution> pick = InvitationRules.pickOrder(
                InvitationRules.DeedType.RAMPAGER_STOPPED, null, "Door",
                List.of(doorOrder), groups, new Random(1));
        assertEquals("order-aurora", pick.orElseThrow().id());
    }
}
