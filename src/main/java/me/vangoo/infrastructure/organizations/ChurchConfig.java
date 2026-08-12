package me.vangoo.infrastructure.organizations;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/** Читає секцію church.* із config.yml; усі мапи мають фолбек-дефолти в коді
 * (Bukkit не перезаписує наявний config.yml при оновленні плагіна). */
public record ChurchConfig(int[] rankThresholds,
                           int rejoinCooldownDays,
                           int tasksRefreshHours,
                           int tasksMaxActive,
                           int tasksSetsPerDay,
                           int orderBrewHours,
                           Map<Integer, Integer> orderPointsBySeq,
                           Map<Integer, Integer> donationIngredientPointsBySeq,
                           Map<Integer, Integer> donationRecipePointsBySeq,
                           Map<Integer, Integer> donationCharacteristicPointsBySeq,
                           int pointsPerCoppet,
                           int vaultSeedBrewsPerRecipe,
                           int vaultSeedCharacteristicsPerSeq,
                           ShrineSettings shrine) {

    /**
     * Кишеньковий світ храмів і шрайни в селах. Окремим записом, а не сімома полями в
     * {@code ChurchConfig}: у нього вже 13 компонентів, і плоский список став би нечитним.
     */
    public record ShrineSettings(boolean pocketWorldEnabled,
                                 int pocketWorldSpacing,
                                 Material focusBlock,
                                 double interactRadius,
                                 boolean ambienceEnabled,
                                 long ambiencePeriodTicks,
                                 double ambienceRadius,
                                 boolean placeNearVillages,
                                 double villageGuardRadius) {}

    private static final int[] DEFAULT_RANK_THRESHOLDS = {0, 200, 600, 1500, 3500};
    private static final Material DEFAULT_FOCUS_BLOCK = Material.BEACON;
    /** Найбільший храм — 39×51×64, тож тісніша сітка ставила б будівлі одна в одну. */
    private static final int MIN_POCKET_SPACING = 128;
    private static final Map<Integer, Integer> DEFAULT_ORDER_POINTS = Map.of(
            9, 60, 8, 90, 7, 140, 6, 200, 5, 280, 4, 380, 3, 500, 2, 650, 1, 850, 0, 1100);
    private static final Map<Integer, Integer> DEFAULT_DONATION_INGREDIENT = Map.of(
            9, 2, 8, 3, 7, 5, 6, 8, 5, 12, 4, 18, 3, 26, 2, 36, 1, 48, 0, 62);
    private static final Map<Integer, Integer> DEFAULT_DONATION_RECIPE = Map.of(
            9, 40, 8, 55, 7, 75, 6, 100, 5, 135, 4, 180, 3, 240, 2, 310, 1, 390, 0, 480);
    private static final Map<Integer, Integer> DEFAULT_DONATION_CHARACTERISTIC = Map.of(
            9, 100, 8, 160, 7, 260, 6, 400, 5, 600, 4, 850, 3, 1150, 2, 1550, 1, 2000, 0, 2600);

    public static ChurchConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        int[] thresholds = DEFAULT_RANK_THRESHOLDS.clone();
        var list = cfg.getIntegerList("church.rank-thresholds");
        if (list.size() == thresholds.length && list.get(0) == 0) {
            for (int i = 0; i < thresholds.length; i++) {
                thresholds[i] = list.get(i);
            }
        } else if (!list.isEmpty()) {
            plugin.getLogger().warning("church.rank-thresholds must be 5 values starting with 0; using defaults");
        }
        return new ChurchConfig(
                thresholds,
                cfg.getInt("church.rejoin-cooldown-days", 3),
                cfg.getInt("church.tasks.refresh-hours", 24),
                cfg.getInt("church.tasks.max-active", 2),
                cfg.getInt("church.tasks.sets-per-day", 5),
                cfg.getInt("church.order.brew-hours", 12),
                loadSeqMap(plugin, "church.order.points-by-seq", DEFAULT_ORDER_POINTS),
                loadSeqMap(plugin, "church.donation.ingredient-points-by-seq", DEFAULT_DONATION_INGREDIENT),
                loadSeqMap(plugin, "church.donation.recipe-points-by-seq", DEFAULT_DONATION_RECIPE),
                loadSeqMap(plugin, "church.donation.characteristic-points-by-seq", DEFAULT_DONATION_CHARACTERISTIC),
                cfg.getInt("church.donation.points-per-coppet", 1),
                cfg.getInt("church.vault.seed.brews-per-recipe", 3),
                cfg.getInt("church.vault.seed.characteristics-per-seq", 1),
                loadShrine(plugin));
    }

    private static ShrineSettings loadShrine(Plugin plugin) {
        var cfg = plugin.getConfig();
        int spacing = Math.max(MIN_POCKET_SPACING,
                cfg.getInt("church.pocket-world.spacing", 512));
        return new ShrineSettings(
                cfg.getBoolean("church.pocket-world.enabled", true),
                spacing,
                focusBlock(plugin, cfg.getString("church.shrine.focus-block")),
                cfg.getDouble("church.shrine.interact-radius", 2.5),
                cfg.getBoolean("church.shrine.ambience-enabled", true),
                cfg.getLong("church.shrine.ambience-period-ticks", 40L),
                cfg.getDouble("church.shrine.ambience-radius", 32.0),
                cfg.getBoolean("church.shrine.place-near-villages", true),
                cfg.getDouble("church.shrine.village-guard-radius", 700.0));
    }

    /**
     * Хибна назва блоку тут не має валити старт: вівтар — гейт кліку, і мовчазний
     * {@code null} перетворив би всі шрайни на декорацію без жодного сліду в лозі.
     */
    private static Material focusBlock(Plugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FOCUS_BLOCK;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("church.shrine.focus-block: unknown block '" + raw
                    + "'; using " + DEFAULT_FOCUS_BLOCK);
            return DEFAULT_FOCUS_BLOCK;
        }
        return material;
    }

    private static Map<Integer, Integer> loadSeqMap(Plugin plugin, String path,
                                                    Map<Integer, Integer> defaults) {
        Map<Integer, Integer> bySeq = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    bySeq.put(Integer.parseInt(key), section.getInt(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning(path + ": bad sequence key '" + key + "', skipped");
                }
            }
        }
        if (bySeq.isEmpty()) {
            bySeq.putAll(defaults);
            plugin.getLogger().info(path + " missing; using built-in defaults");
        }
        return bySeq;
    }
}
