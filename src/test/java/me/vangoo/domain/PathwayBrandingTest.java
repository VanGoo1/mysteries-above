package me.vangoo.domain;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathwayBrandingTest {

    private static final String[] ALL = {
            "Error", "Visionary", "Door", "Justiciar", "WhiteTower", "Fool",
            "Sun", "Tyrant", "HangedMan", "Darkness", "Death", "TwilightGiant",
            "RedPriest", "Demoness", "Hermit", "Paragon", "Mother", "Moon",
            "Abyss", "Chained", "BlackEmperor", "WheelOfFortune"
    };

    @Test
    void everyPathwayHasBranding() {
        assertEquals(22, PathwayBranding.NAMES.size());
        for (String name : ALL) {
            assertTrue(PathwayBranding.NAMES.contains(name), "missing branding for " + name);
            assertNotNull(PathwayBranding.of(name).liquid());
            assertNotNull(PathwayBranding.of(name).text());
        }
    }

    @Test
    void unknownOrNullFallsBackToGray() {
        assertEquals(ChatColor.GRAY, PathwayBranding.of("Nonexistent").text());
        assertEquals(ChatColor.GRAY, PathwayBranding.of(null).text());
        assertEquals(Color.fromRGB(128, 128, 128), PathwayBranding.of(null).liquid());
    }

    @Test
    void preservesExistingErrorColor() {
        assertEquals(Color.fromRGB(26, 0, 181), PathwayBranding.liquidOf("Error"));
    }
}
