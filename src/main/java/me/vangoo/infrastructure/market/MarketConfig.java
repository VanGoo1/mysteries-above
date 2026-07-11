package me.vangoo.infrastructure.market;

import me.vangoo.domain.market.BuybackPriceTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/** Читає секцію market.* із config.yml (через plugin.getConfig(), як creatures.*). */
public record MarketConfig(int intervalDays,
                           int joinWindowMinutes,
                           int durationMinutes,
                           double commissionRate,
                           BuybackPriceTable buyback) {

    /**
     * Дефолтні ціни скупки за одиницю (коппети) для кожної послідовності (9 = найслабша …
     * 0 = найсильніша). Слугують фолбеком, коли на сервері старий config.yml без відповідних
     * секцій — Bukkit НЕ перезаписує наявний config.yml при оновленні плагіна, тож без цих
     * дефолтів організатор відмовляв би скуповувати («не дам і коппета»). Дублюють config.yml.
     */
    private static final Map<Integer, Integer> DEFAULT_INGREDIENT_COPPETS = Map.of(
            9, 3, 8, 5, 7, 8, 6, 13, 5, 20, 4, 32, 3, 50, 2, 75, 1, 105, 0, 140);
    private static final Map<Integer, Integer> DEFAULT_RECIPE_BOOK_COPPETS = Map.of(
            9, 120, 8, 170, 7, 240, 6, 330, 5, 450, 4, 600, 3, 780, 2, 980, 1, 1200, 0, 1500);
    private static final Map<Integer, Integer> DEFAULT_CHARACTERISTIC_COPPETS = Map.of(
            9, 80, 8, 130, 7, 220, 6, 360, 5, 540, 4, 780, 3, 1100, 2, 1500, 1, 2000, 0, 2600);

    public static MarketConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        int intervalDays = cfg.getInt("market.gathering.interval-days", 7);
        int joinWindow = cfg.getInt("market.gathering.join-window-minutes", 5);
        int duration = cfg.getInt("market.gathering.duration-minutes", 15);
        double rate = cfg.getDouble("market.commission-rate", 0.10);

        Map<Integer, Integer> ingredientBySeq = loadSeqMap(plugin,
                "market.buyback.ingredient-coppets-by-seq", DEFAULT_INGREDIENT_COPPETS);
        Map<Integer, Integer> recipeBySeq = loadSeqMap(plugin,
                "market.buyback.recipe-book-coppets-by-seq", DEFAULT_RECIPE_BOOK_COPPETS);
        Map<Integer, Integer> characteristicBySeq = loadSeqMap(plugin,
                "market.buyback.characteristic-coppets-by-seq", DEFAULT_CHARACTERISTIC_COPPETS);

        Map<String, Integer> overrides = new HashMap<>();
        ConfigurationSection overridesSection =
                cfg.getConfigurationSection("market.buyback.overrides");
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                overrides.put(key, overridesSection.getInt(key));
            }
        }
        BuybackPriceTable table = new BuybackPriceTable(
                ingredientBySeq,
                cfg.getInt("market.buyback.ingredient-coppets", 2), // фолбек для інгр. без послідовності
                recipeBySeq, characteristicBySeq, overrides);
        return new MarketConfig(intervalDays, joinWindow, duration, rate, table);
    }

    /** Читає мапу «послідовність → коппети»; якщо секції нема (старий config) — дефолти. */
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
