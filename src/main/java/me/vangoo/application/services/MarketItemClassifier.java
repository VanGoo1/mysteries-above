package me.vangoo.application.services;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.market.MarketItemCategory;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.CurrencyCodec;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Визначає, чи можна виставити предмет на ринок, і його itemKey.
 * Дозволено: інгредієнти (custom items), Характеристики, книги рецептів.
 * Заборонено: зілля, ванільні предмети, монети.
 */
public class MarketItemClassifier {

    /** sequence = −1, якщо послідовність невідома (інгредієнт не входить у жоден рецепт). */
    public record ClassifiedItem(MarketItemCategory category, String itemKey, int sequence) {}

    private final CharacteristicCodec characteristicCodec;
    private final RecipeBookFactory recipeBookFactory;
    private final CustomItemService customItemService;
    private final CurrencyCodec currencyCodec;
    /**
     * itemKey інгредієнта → його послідовність (найсильніша, де він ужитий у рецептах).
     * Інжектується сеттером після побудови потонів (див. ServiceContainer) — потони
     * створюються ПІСЛЯ класифікатора, тож конструкторної залежності тут бути не може.
     */
    private Map<String, Integer> ingredientSequenceByKey = Map.of();

    public MarketItemClassifier(CharacteristicCodec characteristicCodec,
                                RecipeBookFactory recipeBookFactory,
                                CustomItemService customItemService,
                                CurrencyCodec currencyCodec) {
        this.characteristicCodec = characteristicCodec;
        this.recipeBookFactory = recipeBookFactory;
        this.customItemService = customItemService;
        this.currencyCodec = currencyCodec;
    }

    /** Провід: індекс «itemKey інгредієнта → послідовність», побудований з рецептів на старті. */
    public void setIngredientSequenceIndex(Map<String, Integer> index) {
        this.ingredientSequenceByKey = Map.copyOf(index);
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
                    .map(ci -> {
                        String key = "custom:" + ci.id();
                        return new ClassifiedItem(MarketItemCategory.INGREDIENT, key,
                                ingredientSequenceByKey.getOrDefault(key, -1));
                    });
        }
        return Optional.empty();
    }
}
