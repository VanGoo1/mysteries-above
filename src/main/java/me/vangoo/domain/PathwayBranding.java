package me.vangoo.domain;

import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Єдине джерело правди для брендинг-кольору кожного шляху: колір рідини зілля
 * та ChatColor назви (зілля/Характеристики). Лежить у корені domain, де
 * org.bukkit.Color уже дозволений (як у {@link PathwayPotions}).
 */
public final class PathwayBranding {

    public record Branding(Color liquid, ChatColor text) {}

    private static final Branding FALLBACK = new Branding(Color.fromRGB(128, 128, 128), ChatColor.GRAY);

    private static final Map<String, Branding> TABLE = new LinkedHashMap<>();

    private static void put(String name, int r, int g, int b, ChatColor text) {
        TABLE.put(name, new Branding(Color.fromRGB(r, g, b), text));
    }

    static {
        // Наявні 6 (кольори збережено без змін із PotionManager)
        put("Error", 26, 0, 181, ChatColor.DARK_BLUE);
        put("Visionary", 128, 128, 128, ChatColor.GRAY);
        put("Door", 0, 0, 115, ChatColor.BLUE);
        put("Justiciar", 255, 255, 0, ChatColor.YELLOW);
        put("WhiteTower", 255, 0, 50, ChatColor.RED);
        put("Fool", 128, 0, 128, ChatColor.LIGHT_PURPLE);
        // 16 стубів
        put("Sun", 255, 190, 0, ChatColor.GOLD);
        put("Tyrant", 0, 140, 170, ChatColor.DARK_AQUA);
        put("HangedMan", 205, 130, 160, ChatColor.LIGHT_PURPLE);
        put("Darkness", 35, 25, 70, ChatColor.DARK_GRAY);
        put("Death", 150, 165, 140, ChatColor.GRAY);
        put("TwilightGiant", 150, 110, 70, ChatColor.GOLD);
        put("RedPriest", 170, 0, 0, ChatColor.DARK_RED);
        put("Demoness", 200, 0, 120, ChatColor.LIGHT_PURPLE);
        put("Hermit", 0, 175, 200, ChatColor.AQUA);
        put("Paragon", 175, 150, 90, ChatColor.YELLOW);
        put("Mother", 60, 160, 60, ChatColor.GREEN);
        put("Moon", 170, 190, 225, ChatColor.AQUA);
        put("Abyss", 80, 0, 120, ChatColor.DARK_PURPLE);
        put("Chained", 95, 95, 105, ChatColor.GRAY);
        put("BlackEmperor", 45, 0, 55, ChatColor.DARK_PURPLE);
        put("WheelOfFortune", 120, 80, 165, ChatColor.LIGHT_PURPLE);
    }

    public static final Set<String> NAMES = Set.copyOf(TABLE.keySet());

    private PathwayBranding() {}

    public static Branding of(String pathwayName) {
        if (pathwayName == null) return FALLBACK;
        return TABLE.getOrDefault(pathwayName, FALLBACK);
    }

    public static Color liquidOf(String pathwayName) {
        return of(pathwayName).liquid();
    }

    public static ChatColor textOf(String pathwayName) {
        return of(pathwayName).text();
    }
}
