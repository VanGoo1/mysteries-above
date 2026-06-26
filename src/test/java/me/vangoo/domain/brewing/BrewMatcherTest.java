package me.vangoo.domain.brewing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BrewMatcherTest {

    // Fool Seq 5: main = puppeteer_heartstring; aux = spirit_thread_spool, wraithhold_essence
    private BrewRecipe foolSeq5() {
        return new BrewRecipe(
                "Fool", 5,
                Map.of("custom:puppeteer_heartstring", 1),
                Map.of("custom:spirit_thread_spool", 1, "custom:wraithhold_essence", 1)
        );
    }

    private Map<String, Integer> counts(String... keys) {
        java.util.HashMap<String, Integer> m = new java.util.HashMap<>();
        for (String k : keys) m.merge(k, 1, Integer::sum);
        return m;
    }

    @Test
    void classicExactMatchSucceeds() {
        assertTrue(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void missingAuxiliaryFails() {
        assertFalse(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool")));
    }

    @Test
    void extraUnknownItemFails() {
        assertFalse(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence",
                "vanilla:DIRT")));
    }

    @Test
    void wrongCountFails() {
        assertFalse(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicReplacesAllMainsSucceeds() {
        assertTrue(foolSeq5().matches(counts(
                "characteristic:Fool:5",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicWrongPathwayFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Door:5",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicWrongSequenceFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Fool:6",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicPlusLeftoverMainFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Fool:5",
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicMissingAuxiliaryFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Fool:5",
                "custom:spirit_thread_spool")));
    }

    @Test
    void recipeWithNoAuxiliary_singleCharacteristicSucceeds_doubleFails() {
        BrewRecipe noAux = new BrewRecipe("Error", 9,
                Map.of("custom:sphinx_brain", 1), Map.of());
        assertTrue(noAux.matches(counts("characteristic:Error:9")));
        assertFalse(noAux.matches(counts("characteristic:Error:9", "characteristic:Error:9")));
    }

    @Test
    void matcherReturnsFirstMatchingRecipe() {
        BrewMatcher matcher = new BrewMatcher();
        Optional<BrewRecipe> match = matcher.findMatch(
                List.of(foolSeq5()),
                counts("custom:puppeteer_heartstring",
                        "custom:spirit_thread_spool",
                        "custom:wraithhold_essence"));
        assertTrue(match.isPresent());
        assertEquals("Fool", match.get().pathwayName());
        assertEquals(5, match.get().sequence());
    }

    @Test
    void matcherReturnsEmptyWhenNothingMatches() {
        BrewMatcher matcher = new BrewMatcher();
        assertTrue(matcher.findMatch(List.of(foolSeq5()), counts("vanilla:DIRT")).isEmpty());
    }
}
