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

    public static MarketConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        int intervalDays = cfg.getInt("market.gathering.interval-days", 7);
        int joinWindow = cfg.getInt("market.gathering.join-window-minutes", 5);
        int duration = cfg.getInt("market.gathering.duration-minutes", 15);
        double rate = cfg.getDouble("market.commission-rate", 0.10);

        Map<Integer, Integer> bySeq = new HashMap<>();
        ConfigurationSection seqSection =
                cfg.getConfigurationSection("market.buyback.characteristic-coppets-by-seq");
        if (seqSection != null) {
            for (String key : seqSection.getKeys(false)) {
                try {
                    bySeq.put(Integer.parseInt(key), seqSection.getInt(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("market.buyback: bad sequence key '" + key + "', skipped");
                }
            }
        }
        Map<String, Integer> overrides = new HashMap<>();
        ConfigurationSection overridesSection =
                cfg.getConfigurationSection("market.buyback.overrides");
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                overrides.put(key, overridesSection.getInt(key));
            }
        }
        BuybackPriceTable table = new BuybackPriceTable(
                cfg.getInt("market.buyback.ingredient-coppets", 2),
                cfg.getInt("market.buyback.recipe-book-coppets", 10),
                bySeq, overrides);
        return new MarketConfig(intervalDays, joinWindow, duration, rate, table);
    }
}
