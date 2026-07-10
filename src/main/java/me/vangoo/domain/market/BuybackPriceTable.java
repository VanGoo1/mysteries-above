package me.vangoo.domain.market;

import java.util.Map;

/**
 * Прайс скупки організатором (за одиницю, в коппетах). Свідомо нижче ринкових
 * очікувань — «підлога» цін і джерело емісії монет. Дані — з config.yml (market.buyback).
 */
public record BuybackPriceTable(int ingredientCoppets,
                                int recipeBookCoppets,
                                Map<Integer, Integer> characteristicCoppetsBySeq,
                                Map<String, Integer> overridesByItemKey) {

    public PoundMoney unitPriceFor(MarketItemCategory category, int sequenceOrMinus1, String itemKey) {
        Integer override = overridesByItemKey.get(itemKey);
        if (override != null) {
            return PoundMoney.ofCoppets(override);
        }
        return switch (category) {
            case INGREDIENT -> PoundMoney.ofCoppets(ingredientCoppets);
            case RECIPE_BOOK -> PoundMoney.ofCoppets(recipeBookCoppets);
            case CHARACTERISTIC -> PoundMoney.ofCoppets(
                    characteristicCoppetsBySeq.getOrDefault(sequenceOrMinus1, 0));
        };
    }
}
