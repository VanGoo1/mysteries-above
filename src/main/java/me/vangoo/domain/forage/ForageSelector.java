package me.vangoo.domain.forage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Чисте правило вибору допоміжного інгредієнта для фореджу за біомом (аналог CreatureSelector).
 * Зважений детермінований вибір при поданому {@code roll} ∈ [0,1). Без Bukkit, без стану.
 */
public final class ForageSelector {

    private final Map<String, List<ForageEntry>> byBiome;

    public ForageSelector(Map<String, List<ForageEntry>> byBiome) {
        Map<String, List<ForageEntry>> copy = new HashMap<>();
        for (Map.Entry<String, List<ForageEntry>> e : byBiome.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.byBiome = Map.copyOf(copy);
    }

    public Optional<String> pickForBiome(String biome, double roll) {
        if (biome == null) return Optional.empty();
        List<ForageEntry> all = byBiome.get(biome);
        if (all == null) return Optional.empty();

        List<ForageEntry> entries = new ArrayList<>();
        double sum = 0.0;
        for (ForageEntry e : all) {
            if (e.weight() > 0) {
                entries.add(e);
                sum += e.weight();
            }
        }
        if (entries.isEmpty()) return Optional.empty();

        double target = roll * sum;
        double cumulative = 0.0;
        for (ForageEntry e : entries) {
            cumulative += e.weight();
            if (target < cumulative) return Optional.of(e.ingredientId());
        }
        return Optional.of(entries.get(entries.size() - 1).ingredientId());
    }
}
