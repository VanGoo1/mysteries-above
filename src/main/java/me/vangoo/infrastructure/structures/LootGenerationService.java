package me.vangoo.infrastructure.structures;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Сервіс для генерації кастомного луту
 */
public class LootGenerationService {

    private final Plugin plugin;
    private final CustomItemService customItemService;
    private final PotionManager potionManager;
    private final RecipeBookFactory recipeBookFactory;
    private final Random random = new Random();

    public LootGenerationService(
            Plugin plugin,
            CustomItemService customItemService,
            PotionManager potionManager,
            RecipeBookFactory recipeBookFactory) {
        this.plugin = plugin;
        this.customItemService = customItemService;
        this.potionManager = potionManager;
        this.recipeBookFactory = recipeBookFactory;
    }

    /**
     * Випадково вибирає предмет з loot table за вагою
     */
    public LootItem rollItem(LootTableData lootTable) {
        int totalWeight = lootTable.items().stream().mapToInt(LootItem::weight).sum();
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (LootItem item : lootTable.items()) {
            cumulative += item.weight();
            if (roll < cumulative) {
                return item;
            }
        }
        return null;
    }

    /**
     * Розраховує випадкову кількість предмета в межах min-max
     */
    public int calculateAmount(LootItem item) {
        int min = item.minAmount();
        int max = item.maxAmount();
        return (min == max) ? min : (min + random.nextInt(max - min + 1));
    }

    /**
     * Створює ItemStack з ID (підтримує custom, potion, recipe)
     */
    public ItemStack createItemFromId(String itemId) {
        if (itemId.startsWith("potion:")) {
            return createPotion(itemId);
        }

        if (itemId.startsWith("recipe:")) {
            return createRecipeBook(itemId);
        }

        String actualId = itemId.startsWith("custom:") ? itemId.substring(7) : itemId;
        Optional<ItemStack> itemStackOptional = customItemService.createItemStack(actualId);

        if (itemStackOptional.isEmpty()) {
            plugin.getLogger().warning("Failed to create item: " + itemId);
            return null;
        }

        return itemStackOptional.get();
    }

    private ItemStack createPotion(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) {
                plugin.getLogger().warning("Invalid potion format: " + itemId + " (expected: potion:pathway:sequence)");
                return null;
            }

            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);

            if (sequence < 0 || sequence > 9) {
                plugin.getLogger().warning("Invalid sequence in potion: " + itemId + " (must be 0-9)");
                return null;
            }

            return potionManager.createPotionItem(pathway, Sequence.of(sequence));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create potion: " + itemId + " - " + e.getMessage());
            return null;
        }
    }

    private ItemStack createRecipeBook(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) {
                plugin.getLogger().warning("Invalid recipe format: " + itemId + " (expected: recipe:pathway:sequence)");
                return null;
            }

            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);

            if (sequence < 0 || sequence > 9) {
                plugin.getLogger().warning("Invalid sequence in recipe: " + itemId + " (must be 0-9)");
                return null;
            }

            PathwayPotions pathwayPotions = potionManager.getPotionsPathway(pathway).orElse(null);
            if (pathwayPotions == null) {
                plugin.getLogger().warning("Unknown pathway in recipe: " + pathway);
                return null;
            }

            return recipeBookFactory.createRecipeBook(pathwayPotions, sequence);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create recipe book: " + itemId + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Генерує список предметів з loot table
     * @param lootTable таблиця луту
     * @param count кількість предметів для генерації
     * @param allowDuplicates чи дозволяти дублікати
     * @return список згенерованих ItemStack
     */
    public List<ItemStack> generateLoot(LootTableData lootTable, int count, boolean allowDuplicates) {
        List<ItemStack> generatedLoot = new ArrayList<>();
        Set<String> addedItems = new HashSet<>();

        int attempts = 0;
        int maxAttempts = count * 10;

        while (generatedLoot.size() < count && attempts < maxAttempts) {
            attempts++;

            LootItem selectedItem = rollItem(lootTable);
            if (selectedItem == null) continue;

            // Уникаємо дублювання якщо не дозволено
            if (!allowDuplicates && addedItems.contains(selectedItem.itemId())
                    && addedItems.size() < lootTable.items().size()) {
                continue;
            }

            ItemStack itemStack = createItemFromId(selectedItem.itemId());
            if (itemStack != null && itemStack.getType() != Material.BARRIER) {
                int amount = calculateAmount(selectedItem);
                itemStack.setAmount(amount);

                generatedLoot.add(itemStack);
                addedItems.add(selectedItem.itemId());
            }
        }

        return generatedLoot;
    }
}