package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.vangoo.domain.organizations.FavorOptions.Option.*;
import static org.junit.jupiter.api.Assertions.*;

class FavorOptionsTest {

    private static List<FavorOptions.Option> opts(boolean knowsRecipe, TaskWeight w,
                                                  OrderRank rank, boolean intel) {
        return FavorOptions.available(new FavorOptions.Context(knowsRecipe, w, rank, intel));
    }

    @Test
    void withoutNextRecipeTheOnlyMaterialAskIsTheRecipe() {
        List<FavorOptions.Option> options = opts(false, TaskWeight.STANDARD, OrderRank.PAWN, false);
        assertTrue(options.contains(RECIPE_KNOWLEDGE));
        assertFalse(options.contains(INGREDIENTS));
        assertFalse(options.contains(CHARACTERISTIC));
    }

    @Test
    void knowingRecipeUnlocksIngredientsInsteadOfRecipe() {
        List<FavorOptions.Option> options = opts(true, TaskWeight.STANDARD, OrderRank.PAWN, false);
        assertFalse(options.contains(RECIPE_KNOWLEDGE));
        assertTrue(options.contains(INGREDIENTS));
    }

    @Test
    void characteristicNeedsMajorFavorAndTrustedRank() {
        assertFalse(opts(true, TaskWeight.STANDARD, OrderRank.TRUSTED, false).contains(CHARACTERISTIC));
        assertFalse(opts(true, TaskWeight.MAJOR, OrderRank.BLADE, false).contains(CHARACTERISTIC));
        assertTrue(opts(true, TaskWeight.MAJOR, OrderRank.TRUSTED, false).contains(CHARACTERISTIC));
    }

    @Test
    void lightFavorBuysOnlyInformation() {
        List<FavorOptions.Option> options = opts(false, TaskWeight.LIGHT, OrderRank.MAGISTER, true);
        assertEquals(List.of(HUNT_INFO, VAULT_INTEL), options);
    }

    @Test
    void vaultIntelRequiresFreshIntel() {
        assertFalse(opts(true, TaskWeight.MAJOR, OrderRank.TRUSTED, false).contains(VAULT_INTEL));
        assertTrue(opts(true, TaskWeight.LIGHT, OrderRank.PAWN, true).contains(VAULT_INTEL));
    }

    @Test
    void servicesNeedMajorFavor() {
        List<FavorOptions.Option> major = opts(true, TaskWeight.MAJOR, OrderRank.PAWN, false);
        assertTrue(major.contains(CLEAR_COOLDOWN));
        assertTrue(major.contains(FALSE_PAPERS));
        List<FavorOptions.Option> standard = opts(true, TaskWeight.STANDARD, OrderRank.PAWN, false);
        assertFalse(standard.contains(CLEAR_COOLDOWN));
        assertFalse(standard.contains(FALSE_PAPERS));
    }

    @Test
    void requiredWeightMatchesGates() {
        assertEquals(TaskWeight.LIGHT, FavorOptions.requiredWeight(HUNT_INFO));
        assertEquals(TaskWeight.STANDARD, FavorOptions.requiredWeight(RECIPE_KNOWLEDGE));
        assertEquals(TaskWeight.STANDARD, FavorOptions.requiredWeight(INGREDIENTS));
        assertEquals(TaskWeight.MAJOR, FavorOptions.requiredWeight(CHARACTERISTIC));
        assertEquals(TaskWeight.MAJOR, FavorOptions.requiredWeight(CLEAR_COOLDOWN));
    }
}
