package me.vangoo.domain.organizations;

import me.vangoo.domain.brewing.BrewRecipe;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сховище церкви: itemKey → кількість. Книги рецептів — знання-гейт (не списуються);
 * інгредієнти й Характеристики списуються у варіння замовлень.
 */
public class ChurchVault {

    private final Map<String, Integer> items = new HashMap<>();

    public void add(String itemKey, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        items.merge(itemKey, amount, Integer::sum);
    }

    public int amountOf(String itemKey) {
        return items.getOrDefault(itemKey, 0);
    }

    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(items);
    }

    public void restore(Map<String, Integer> saved) {
        items.clear();
        saved.forEach((k, v) -> {
            if (v != null && v > 0) {
                items.put(k, v);
            }
        });
    }

    public boolean hasRecipeKnowledge(String pathwayName, int sequence) {
        return amountOf("recipe:" + pathwayName + ":" + sequence) > 0;
    }

    /** Порожня мапа = можна варити (класикою або Характеристикою). Інакше — брак класики. */
    public Map<String, Integer> missingFor(BrewRecipe recipe) {
        Map<String, Integer> classicMissing = shortfall(classicRequirement(recipe));
        if (classicMissing.isEmpty()) {
            return classicMissing;
        }
        if (shortfall(characteristicRequirement(recipe)).isEmpty()) {
            return Map.of();
        }
        return classicMissing;
    }

    /** Атомарне списання: класика пріоритетна; false — бракує обох варіантів. */
    public boolean consumeFor(BrewRecipe recipe) {
        Map<String, Integer> requirement = classicRequirement(recipe);
        if (!shortfall(requirement).isEmpty()) {
            requirement = characteristicRequirement(recipe);
            if (!shortfall(requirement).isEmpty()) {
                return false;
            }
        }
        requirement.forEach((key, amount) -> {
            int rest = items.get(key) - amount;
            if (rest == 0) {
                items.remove(key);
            } else {
                items.put(key, rest);
            }
        });
        return true;
    }

    private static Map<String, Integer> classicRequirement(BrewRecipe recipe) {
        Map<String, Integer> required = new HashMap<>(recipe.mainCounts());
        recipe.auxCounts().forEach((k, v) -> required.merge(k, v, Integer::sum));
        return required;
    }

    private static Map<String, Integer> characteristicRequirement(BrewRecipe recipe) {
        Map<String, Integer> required = new HashMap<>(recipe.auxCounts());
        required.merge(recipe.characteristicKey(), 1, Integer::sum);
        return required;
    }

    /** Атомарне зняття (крадіжка рейду 6c); false — бракує, нічого не змінюється. */
    public boolean take(String itemKey, int amount) {
        int have = amountOf(itemKey);
        if (have < amount) {
            return false;
        }
        if (have == amount) {
            items.remove(itemKey);
        } else {
            items.put(itemKey, have - amount);
        }
        return true;
    }

    private Map<String, Integer> shortfall(Map<String, Integer> requirement) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        requirement.forEach((key, amount) -> {
            int lack = amount - amountOf(key);
            if (lack > 0) {
                missing.put(key, lack);
            }
        });
        return missing;
    }
}
