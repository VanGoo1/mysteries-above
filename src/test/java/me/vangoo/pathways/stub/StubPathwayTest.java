package me.vangoo.pathways.stub;

import me.vangoo.domain.entities.PathwayGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StubPathwayTest {

    private static final List<String> TEN = List.of(
            "S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9");

    @Test
    void nameComesFromConstructorNotClassName() {
        StubPathway p = new StubPathway(PathwayGroup.EternalDarkness, "Darkness", TEN);
        assertEquals("Darkness", p.getName());
        assertEquals(PathwayGroup.EternalDarkness, p.getGroup());
        assertEquals("S9", p.getSequenceName(9));
    }

    @Test
    void hasNoAbilities() {
        StubPathway p = new StubPathway(PathwayGroup.EternalDarkness, "Death", TEN);
        assertTrue(p.GetAbilitiesForSequence(0).isEmpty());
        assertTrue(p.GetAbilitiesForSequence(9).isEmpty());
    }
}
