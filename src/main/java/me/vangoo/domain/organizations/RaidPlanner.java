package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Чиста математика рейду: шанс тривоги й вибірка здобичі зі знімка сховища церкви.
 * Категорії за вагою: інгредієнти 70, книги рецептів 20, Характеристики 10 —
 * Характеристики в пулі ЛИШЕ зі свіжими розвідданими (джекпот).
 */
public final class RaidPlanner {

    private static final int INGREDIENT_WEIGHT = 70;
    private static final int RECIPE_WEIGHT = 20;
    private static final int CHARACTERISTIC_WEIGHT = 10;

    private RaidPlanner() {}

    public static double alarmChancePerSecond(double base, boolean hasIntel, double intelFactor) {
        return hasIntel ? base * intelFactor : base;
    }

    /** До {@code picks} витягів; кожен витяг — 1..2 одиниці одного ключа, не більше наявного. */
    public static Map<String, Integer> rollLoot(Map<String, Integer> vaultSnapshot, int picks,
                                                boolean includeCharacteristics, Random random) {
        Map<String, Integer> remaining = new HashMap<>(vaultSnapshot);
        if (!includeCharacteristics) {
            remaining.keySet().removeIf(k -> k.startsWith("characteristic:"));
        }
        Map<String, Integer> loot = new LinkedHashMap<>();
        for (int i = 0; i < picks; i++) {
            List<String> ingredients = keysWithPrefix(remaining, "custom:");
            List<String> recipes = keysWithPrefix(remaining, "recipe:");
            List<String> characteristics = keysWithPrefix(remaining, "characteristic:");
            int totalWeight = (ingredients.isEmpty() ? 0 : INGREDIENT_WEIGHT)
                    + (recipes.isEmpty() ? 0 : RECIPE_WEIGHT)
                    + (characteristics.isEmpty() ? 0 : CHARACTERISTIC_WEIGHT);
            if (totalWeight == 0) {
                break;
            }
            int roll = random.nextInt(totalWeight);
            List<String> pool;
            if (!ingredients.isEmpty() && roll < INGREDIENT_WEIGHT) {
                pool = ingredients;
            } else if (!recipes.isEmpty()
                    && roll < (ingredients.isEmpty() ? 0 : INGREDIENT_WEIGHT) + RECIPE_WEIGHT) {
                pool = recipes;
            } else if (!characteristics.isEmpty()) {
                pool = characteristics;
            } else if (!recipes.isEmpty()) {
                pool = recipes;
            } else {
                pool = ingredients;
            }
            String key = pool.get(random.nextInt(pool.size()));
            int available = remaining.get(key);
            int amount = Math.min(available, 1 + random.nextInt(2));
            loot.merge(key, amount, Integer::sum);
            if (available - amount <= 0) {
                remaining.remove(key);
            } else {
                remaining.put(key, available - amount);
            }
        }
        return loot;
    }

    private static List<String> keysWithPrefix(Map<String, Integer> map, String prefix) {
        List<String> keys = new ArrayList<>();
        for (String key : map.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.sort(String::compareTo); // детермінізм для тестів
        return keys;
    }
}
