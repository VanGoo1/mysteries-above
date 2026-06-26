package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.BrewMatcher;
import me.vangoo.domain.brewing.BrewRecipe;
import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service: Handles potion crafting logic.
 * Зіставлення (правило) делегується в чистий {@link BrewMatcher}/{@link BrewRecipe}; цей сервіс лише
 * витягує рядкові ключі з предметів і гейтить за знанням рецепта.
 */
public class PotionCraftingService {
    private final PotionManager potionManager;
    private final RecipeUnlockService recipeUnlockService;
    private final CustomItemService customItemService;
    private final CharacteristicCodec characteristicCodec;
    private final BrewMatcher brewMatcher = new BrewMatcher();

    public PotionCraftingService(
            PotionManager potionManager,
            RecipeUnlockService recipeUnlockService,
            CustomItemService customItemService,
            CharacteristicCodec characteristicCodec) {
        this.potionManager = potionManager;
        this.recipeUnlockService = recipeUnlockService;
        this.customItemService = customItemService;
        this.characteristicCodec = characteristicCodec;
    }

    public record CraftingResult(
            boolean success,
            String pathwayName,
            int sequence,
            ItemStack potion,
            String message
    ) {
        public static CraftingResult success(String pathwayName, int sequence, ItemStack potion) {
            return new CraftingResult(true, pathwayName, sequence, potion, "Зілля успішно створено!");
        }

        public static CraftingResult failure(String message) {
            return new CraftingResult(false, null, -1, null, message);
        }
    }

    /** Try to craft potion from ingredients. */
    public CraftingResult trycraft(UUID playerId, List<ItemStack> thrownItems) {
        if (thrownItems.isEmpty()) {
            return CraftingResult.failure("Недостатньо інгредієнтів");
        }

        Map<String, Integer> provided = countItems(thrownItems);

        // Збираємо лише рецепти, які гравець РОЗБЛОКУВАВ (гейт знання для обох шляхів зварювання).
        List<BrewRecipe> unlocked = new ArrayList<>();
        for (PathwayPotions pathwayPotions : potionManager.getPotions()) {
            String pathwayName = pathwayPotions.getPathway().getName();
            for (int sequence = 9; sequence >= 0; sequence--) {
                ItemStack[] main = pathwayPotions.getMainIngredients(sequence);
                ItemStack[] aux = pathwayPotions.getAuxiliaryIngredients(sequence);
                if ((main == null || main.length == 0) && (aux == null || aux.length == 0)) {
                    continue;
                }
                if (!recipeUnlockService.canCraftPotion(playerId, pathwayName, sequence)) {
                    continue;
                }
                Map<String, Integer> mainCounts = countItems(main == null ? List.of() : Arrays.asList(main));
                Map<String, Integer> auxCounts = countItems(aux == null ? List.of() : Arrays.asList(aux));
                unlocked.add(new BrewRecipe(pathwayName, sequence, mainCounts, auxCounts));
            }
        }

        Optional<BrewRecipe> match = brewMatcher.findMatch(unlocked, provided);
        if (match.isEmpty()) {
            return CraftingResult.failure("Інгредієнти не підходять для жодного рецепту");
        }

        BrewRecipe recipe = match.get();
        ItemStack potion = potionManager.createPotionItem(recipe.pathwayName(), Sequence.of(recipe.sequence()));
        return CraftingResult.success(recipe.pathwayName(), recipe.sequence(), potion);
    }

    /** Count items by key (custom item / vanilla / characteristic). */
    private Map<String, Integer> countItems(List<ItemStack> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            counts.merge(getItemKey(item), item.getAmount(), Integer::sum);
        }
        return counts;
    }

    /** Unique key for an item. Характеристика перевіряється ПЕРШОЮ (вона не custom item). */
    private String getItemKey(ItemStack item) {
        Optional<Characteristic> characteristic = characteristicCodec.read(item);
        if (characteristic.isPresent()) {
            return characteristic.get().itemKey();
        }
        if (customItemService.isCustomItem(item)) {
            return customItemService.getCustomItem(item)
                    .map(customItem -> "custom:" + customItem.id())
                    .orElse("vanilla:" + item.getType().name());
        }
        return "vanilla:" + item.getType().name();
    }
}
