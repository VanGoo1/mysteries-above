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
        put("Error", 102, 153, 161, ChatColor.BLUE);
        put("Visionary", 143, 181, 241, ChatColor.GRAY);
        put("Door", 106, 230, 247, ChatColor.AQUA);
        put("Justiciar", 235, 195, 142, ChatColor.GOLD);
        put("WhiteTower",118, 134, 225, ChatColor.AQUA);
        put("Fool", 117, 75, 38, ChatColor.LIGHT_PURPLE);

        put("Sun", 251, 232, 107, ChatColor.YELLOW);
        put("Tyrant", 70, 115, 199, ChatColor.DARK_AQUA);
        put("HangedMan", 204, 54, 53, ChatColor.DARK_RED);
        put("Darkness", 127, 149, 185, ChatColor.DARK_GRAY);
        put("Death", 114, 143, 108, ChatColor.DARK_GREEN);
        put("TwilightGiant",232, 120, 99, ChatColor.GOLD);
        put("RedPriest", 255, 71, 58, ChatColor.RED);
        put("Demoness", 210, 64, 159, ChatColor.LIGHT_PURPLE);
        put("Hermit",124, 98, 179, ChatColor.DARK_PURPLE);
        put("Paragon", 243, 145, 75, ChatColor.GOLD);
        put("Mother", 60, 183, 153, ChatColor.GREEN);
        put("Moon", 255, 96, 104, ChatColor.RED);
        put("Abyss", 132, 31, 17, ChatColor.BLACK);
        put("Chained", 67, 63, 107, ChatColor.DARK_GRAY);
        put("BlackEmperor", 29, 35, 65, ChatColor.DARK_BLUE);
        put("WheelOfFortune", 161, 206, 215, ChatColor.GRAY);
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
