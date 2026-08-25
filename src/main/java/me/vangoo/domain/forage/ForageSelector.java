package me.vangoo.domain.forage;

import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.creatures.ConvergenceBias;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Чисте правило вибору допоміжного інгредієнта для фореджу за біомом (аналог CreatureSelector).
 * Зважений детермінований вибір при поданому {@code roll} ∈ [0,1). Без Bukkit, без стану.
 *
 * <p>Закон конвергенції діє тут так само, як на спавні істот: інгредієнт із рецепта шляху
 * гравця важить більше — найбільше той, що потрібен для НАСТУПНОЇ послідовності. Це ВАГОВИЙ
 * ухил у межах таблиці біому, а не фільтр: чужі інгредієнти далі випадають, і загальна
 * ймовірність появи ноди не змінюється (її вирішує спавнер до виклику).
 */
public final class ForageSelector {

    private static final double NEXT_NEEDED_WEIGHT = 4.0; // sequenceLevel - 1
    private static final double CURRENT_WEIGHT = 2.0;     // sequenceLevel

    private final Map<String, List<ForageEntry>> byBiome;
    /** ingredientId -> {"pathway:sequence"} усіх рецептів, де він потрібен (шлях у нижньому регістрі). */
    private final Map<String, Set<String>> recipeSlots;

    public ForageSelector(Map<String, List<ForageEntry>> byBiome) {
        this(byBiome, Map.of());
    }

    public ForageSelector(Map<String, List<ForageEntry>> byBiome,
                          Map<String, Map<Integer, RecipeDefinition>> recipesByPathway) {
        Map<String, List<ForageEntry>> copy = new HashMap<>();
        for (Map.Entry<String, List<ForageEntry>> e : byBiome.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.byBiome = Map.copyOf(copy);

        Map<String, Set<String>> slots = new HashMap<>();
        recipesByPathway.forEach((pathway, bySequence) -> bySequence.forEach((sequence, def) -> {
            String slot = slot(pathway, sequence);
            List<String> ids = new ArrayList<>(def.mainIds());
            ids.addAll(def.auxIds());
            for (String id : ids) {
                slots.computeIfAbsent(id, k -> new HashSet<>()).add(slot);
            }
        }));
        this.recipeSlots = Map.copyOf(slots);
    }

    public Optional<String> pickForBiome(String biome, double roll) {
        return pickForBiome(biome, null, roll);
    }

    public Optional<String> pickForBiome(String biome, ConvergenceBias bias, double roll) {
        if (biome == null) return Optional.empty();
        List<ForageEntry> all = byBiome.get(biome);
        if (all == null) return Optional.empty();

        List<ForageEntry> entries = new ArrayList<>();
        for (ForageEntry e : all) {
            if (e.weight() > 0) entries.add(e);
        }
        if (entries.isEmpty()) return Optional.empty();

        double[] weights = new double[entries.size()];
        double sum = 0.0;
        for (int i = 0; i < entries.size(); i++) {
            weights[i] = entries.get(i).weight() * multiplier(entries.get(i).ingredientId(), bias);
            sum += weights[i];
        }

        double target = roll * sum;
        double cumulative = 0.0;
        for (int i = 0; i < entries.size(); i++) {
            cumulative += weights[i];
            if (target < cumulative) return Optional.of(entries.get(i).ingredientId());
        }
        return Optional.of(entries.get(entries.size() - 1).ingredientId());
    }

    private double multiplier(String ingredientId, ConvergenceBias bias) {
        if (bias == null || bias.pathway() == null) return 1.0;
        Set<String> slots = recipeSlots.get(ingredientId);
        if (slots == null) return 1.0;
        if (slots.contains(slot(bias.pathway(), bias.sequenceLevel() - 1))) return NEXT_NEEDED_WEIGHT;
        if (slots.contains(slot(bias.pathway(), bias.sequenceLevel()))) return CURRENT_WEIGHT;
        return 1.0;
    }

    private static String slot(String pathway, int sequence) {
        return pathway.toLowerCase() + ":" + sequence;
    }
}
