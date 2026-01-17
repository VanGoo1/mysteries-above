package me.vangoo.infrastructure.structures;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.Sequence;

import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

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
     * Основний метод генерації (без врахування гравця)
     */
    public List<ItemStack> generateLoot(LootTableData lootTable, int count, boolean allowDuplicates) {
        return generateLoot(lootTable, count, allowDuplicates, null);
    }

    /**
     * "Розумна" генерація луту з урахуванням рівня гравця.
     * Якщо випадає предмет слабший за гравця, робиться до 2-х спроб перекинути "кубик".
     *
     * @param beyonder гравець, для якого генерується лут (може бути null)
     */
    public List<ItemStack> generateLoot(LootTableData lootTable, int count, boolean allowDuplicates, Beyonder beyonder) {
        List<ItemStack> generatedLoot = new ArrayList<>();
        Set<String> addedItems = new HashSet<>();

        int attempts = 0;
        int maxAttempts = count * 10; // Запобіжник від нескінченного циклу

        while (generatedLoot.size() < count && attempts < maxAttempts) {
            attempts++;

            LootItem selectedItem = rollItemWithReroll(lootTable, beyonder);

            if (selectedItem == null) continue;

            // Уникаємо дублювання
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

    /**
     * Вибирає предмет, застосовуючи логіку реролу, якщо предмет "сміттєвий" для поточного гравця
     */
    private LootItem rollItemWithReroll(LootTableData lootTable, Beyonder beyonder) {
        LootItem candidate = rollItem(lootTable);

        // Якщо гравця немає або випало нічого, повертаємо як є
        if (beyonder == null || candidate == null) {
            return candidate;
        }

        int playerSequence = beyonder.getSequenceLevel();
        int maxRerolls = 2;
        int currentReroll = 0;

        // Поки у нас є спроби реролу
        while (currentReroll < maxRerolls) {
            int itemSequence = getSequenceFromId(candidate.itemId());

            // Логіка:
            // 1. Якщо це не Sequence предмет (-1) -> Все ок, повертаємо.
            // 2. Якщо предмет сильніший або рівний гравцю (itemSequence <= playerSequence) -> Все ок (наприклад, гравець 5, предмет 4).
            // Пам'ятаємо: 0 - найсильніший, 9 - найслабший.
            if (itemSequence == -1 || itemSequence <= playerSequence) {
                return candidate;
            }

            // Якщо ми тут -> предмет слабший за гравця (наприклад, гравець 5, предмет 9).
            // Робимо рерол.
            LootItem newCandidate = rollItem(lootTable);

            if (newCandidate != null) {
                candidate = newCandidate;
            }

            currentReroll++;
        }

        // Якщо витратили всі спроби, повертаємо останнє, що випало
        return candidate;
    }

    /**
     * Парсить Sequence ID з рядка (potion:pathway:sequence або recipe:pathway:sequence)
     * Повертає -1, якщо це не предмет послідовності.
     */
    private int getSequenceFromId(String itemId) {
        if (itemId == null) return -1;

        // Формат: type:pathway:sequence
        // Приклади: "potion:sun:9", "recipe:fool:5"
        if (!itemId.startsWith("potion:") && !itemId.startsWith("recipe:")) {
            return -1;
        }

        String[] parts = itemId.split(":");
        if (parts.length < 3) return -1;

        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

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

    public int calculateAmount(LootItem item) {
        int min = item.minAmount();
        int max = item.maxAmount();
        return (min == max) ? min : (min + random.nextInt(max - min + 1));
    }

    public ItemStack createItemFromId(String itemId) {
        if (itemId.startsWith("potion:")) return createPotion(itemId);
        if (itemId.startsWith("recipe:")) return createRecipeBook(itemId);

        String actualId = itemId.startsWith("custom:") ? itemId.substring(7) : itemId;
        return customItemService.createItemStack(actualId).orElse(null);
    }

    private ItemStack createPotion(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) return null;
            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);
            return potionManager.createPotionItem(pathway, Sequence.of(sequence));
        } catch (Exception e) { return null; }
    }

    private ItemStack createRecipeBook(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) return null;
            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);
            PathwayPotions pp = potionManager.getPotionsPathway(pathway).orElse(null);
            if (pp == null) return null;
            return recipeBookFactory.createRecipeBook(pp, sequence);
        } catch (Exception e) { return null; }
    }
}