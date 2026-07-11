package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Будує індекс «itemKey інгредієнта → послідовність» з рецептів усіх шляхів.
 * Послідовність інгредієнта = НАЙНИЖЧА (найсильніша), у рецепті якої він трапляється —
 * так ціна скупки відображає найцінніше застосування інгредієнта. Викликається одноразово
 * на старті (ServiceContainer) після побудови потонів; результат інжектиться в
 * {@link MarketItemClassifier}.
 */
public final class IngredientSequenceIndex {

    private IngredientSequenceIndex() {}

    public static Map<String, Integer> build(List<PathwayPotions> allPotions,
                                             CustomItemService customItemService) {
        Map<String, Integer> index = new HashMap<>();
        for (PathwayPotions potions : allPotions) {
            for (int seq = 0; seq <= 9; seq++) {
                ItemStack[] ingredients = potions.getIngredients(seq);
                if (ingredients == null) {
                    continue;
                }
                int sequence = seq;
                for (ItemStack ingredient : ingredients) {
                    customItemService.getCustomItem(ingredient).ifPresent(ci ->
                            index.merge("custom:" + ci.id(), sequence, Math::min));
                }
            }
        }
        return index;
    }
}
