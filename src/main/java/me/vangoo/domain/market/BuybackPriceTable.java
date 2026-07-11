package me.vangoo.domain.market;

import java.util.Map;

/**
 * Прайс скупки організатором (за одиницю, в коппетах). Свідомо нижче ринкових
 * очікувань — «підлога» цін і джерело емісії монет. Дані — з config.yml (market.buyback).
 * Усі категорії скейляться від послідовності предмета (9 = найслабша … 0 = найсильніша):
 * інгредієнт — за послідовністю рецепта, де він ужитий (виводить {@code MarketItemClassifier}),
 * книга рецептів і Характеристика — за власною послідовністю. Інгредієнт без послідовності
 * (не входить у жоден рецепт) отримує плоский {@code ingredientFallbackCoppets}.
 */
public record BuybackPriceTable(Map<Integer, Integer> ingredientCoppetsBySeq,
                                int ingredientFallbackCoppets,
                                Map<Integer, Integer> recipeBookCoppetsBySeq,
                                Map<Integer, Integer> characteristicCoppetsBySeq,
                                Map<String, Integer> overridesByItemKey) {

    public PoundMoney unitPriceFor(MarketItemCategory category, int sequenceOrMinus1, String itemKey) {
        Integer override = overridesByItemKey.get(itemKey);
        if (override != null) {
            return PoundMoney.ofCoppets(override);
        }
        return switch (category) {
            case INGREDIENT -> PoundMoney.ofCoppets(
                    ingredientCoppetsBySeq.getOrDefault(sequenceOrMinus1, ingredientFallbackCoppets));
            case RECIPE_BOOK -> PoundMoney.ofCoppets(
                    recipeBookCoppetsBySeq.getOrDefault(sequenceOrMinus1, 0));
            case CHARACTERISTIC -> PoundMoney.ofCoppets(
                    characteristicCoppetsBySeq.getOrDefault(sequenceOrMinus1, 0));
        };
    }
}
