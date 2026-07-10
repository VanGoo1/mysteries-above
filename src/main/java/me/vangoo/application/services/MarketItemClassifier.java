package me.vangoo.application.services;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.market.MarketItemCategory;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.CurrencyCodec;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Визначає, чи можна виставити предмет на ринок, і його itemKey.
 * Дозволено: інгредієнти (custom items), Характеристики, книги рецептів.
 * Заборонено: зілля, ванільні предмети, монети.
 */
public class MarketItemClassifier {

    /** sequence = −1, якщо категорія без послідовності (інгредієнт). */
    public record ClassifiedItem(MarketItemCategory category, String itemKey, int sequence) {}

    private final CharacteristicCodec characteristicCodec;
    private final RecipeBookFactory recipeBookFactory;
    private final CustomItemService customItemService;
    private final CurrencyCodec currencyCodec;

    public MarketItemClassifier(CharacteristicCodec characteristicCodec,
                                RecipeBookFactory recipeBookFactory,
                                CustomItemService customItemService,
                                CurrencyCodec currencyCodec) {
        this.characteristicCodec = characteristicCodec;
        this.recipeBookFactory = recipeBookFactory;
        this.customItemService = customItemService;
        this.currencyCodec = currencyCodec;
    }

    public Optional<ClassifiedItem> classify(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Optional.empty();
        }
        if (currencyCodec.isPound(item) || currencyCodec.isCoppet(item)) {
            return Optional.empty(); // монети — засіб платежу, не товар
        }
        Optional<Characteristic> characteristic = characteristicCodec.read(item);
        if (characteristic.isPresent()) {
            Characteristic c = characteristic.get();
            return Optional.of(new ClassifiedItem(
                    MarketItemCategory.CHARACTERISTIC, c.itemKey(), c.sequence()));
        }
        if (recipeBookFactory.isRecipeBook(item)) {
            Optional<String> pathway = recipeBookFactory.getPathwayName(item);
            Optional<Integer> sequence = recipeBookFactory.getSequence(item);
            if (pathway.isPresent() && sequence.isPresent()) {
                return Optional.of(new ClassifiedItem(MarketItemCategory.RECIPE_BOOK,
                        "recipe:" + pathway.get() + ":" + sequence.get(), sequence.get()));
            }
            return Optional.empty();
        }
        if (customItemService.isCustomItem(item)) {
            return customItemService.getCustomItem(item)
                    .map(ci -> new ClassifiedItem(MarketItemCategory.INGREDIENT, "custom:" + ci.id(), -1));
        }
        return Optional.empty();
    }
}
