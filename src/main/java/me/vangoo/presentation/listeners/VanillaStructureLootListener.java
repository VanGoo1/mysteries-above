package me.vangoo.presentation.listeners;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Listener для додавання кастомного лута до ванільних структур Minecraft
 */
public class VanillaStructureLootListener implements Listener {

    private final Logger logger;
    private final CustomItemService customItemService;
    private final PotionManager potionManager;
    private final RecipeBookFactory recipeBookFactory;
    private final LootTableData globalLootTable;
    private final Random random = new Random();

    // Ванільні структури до яких додається лут
    private final Map<String, Double> enabledStructures;

    public VanillaStructureLootListener(
            Plugin plugin,
            CustomItemService customItemService,
            PotionManager potionManager,
            RecipeBookFactory recipeBookFactory,
            LootTableData globalLootTable) {
        this.logger = plugin.getLogger();
        this.customItemService = customItemService;
        this.potionManager = potionManager;
        this.recipeBookFactory = recipeBookFactory;
        this.globalLootTable = globalLootTable;
        this.enabledStructures = loadEnabledVanillaStructures();

        logger.info("VanillaStructureLootListener initialized for " + enabledStructures.size() + " structure types");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent event) {
        // Отримуємо ключ loot table
        String lootTableKey = event.getLootTable().getKey().toString();

        double chance = getStructureChance(lootTableKey);

        if (chance <= 0.0) {
            return;
        }

        if (random.nextDouble() > chance) {
            // logger.fine("Chance failed for " + lootTableKey + " (Chance: " + chance + ")");
            return;
        }
        // Додаємо кастомні предмети
        addCustomLoot(event, lootTableKey);
    }

    private double getStructureChance(String lootTableKey) {
        for (Map.Entry<String, Double> entry : enabledStructures.entrySet()) {
            if (lootTableKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0.0;
    }

    private java.util.Map<String, Double> loadEnabledVanillaStructures() {
        Map<String, Double> structures = new java.util.HashMap<>();


        structures.put("mansion", 0.8);
        structures.put("ancient_city", 0.7);
        structures.put("bastion", 0.7);
        structures.put("nether_bridge", 0.5);
        structures.put("end_city", 0.5);
        structures.put("stronghold", 0.6);
        structures.put("jungle_temple", 0.6);
        structures.put("desert_pyramid", 0.3);
        structures.put("igloo", 0.9);
        structures.put("pillager_outpost", 0.3);
        structures.put("ocean_ruin_warm", 0.4);
        structures.put("ocean_ruin_cold", 0.4);
        structures.put("buried_treasure", 0.3);
        structures.put("shipwreck", 0.2);
        structures.put("mineshaft", 0.3);
        structures.put("simple_dungeon", 0.2);
        structures.put("ruined_portal", 0.1);

        logger.info("Enabled vanilla structures for custom loot: " + structures.size());
        return structures;
    }

    /**
     * Додає кастомні предмети до лута
     */
    private void addCustomLoot(LootGenerateEvent event, String lootTableKey) {
        if (globalLootTable == null || globalLootTable.items().isEmpty()) {
            return;
        }

        List<ItemStack> currentLoot = event.getLoot();
        int totalWeight = globalLootTable.items().stream().mapToInt(LootItem::weight).sum();

        // Визначаємо скільки предметів додати
//      int minItems = globalLootTable.minItems();
//      int maxItems = globalLootTable.maxItems();
//      int itemsToAdd = minItems + random.nextInt(maxItems - minItems + 1);

        int itemsToAdd = (Math.random() <= 0.5) ? 1 : 2;
        logger.fine("Adding " + itemsToAdd + " custom items to " + lootTableKey);

        Set<String> addedItems = new HashSet<>();
        int addedCount = 0;
        int attempts = 0;
        int maxAttempts = 35; // itemsToAdd * 10

        while (addedCount < itemsToAdd && attempts < maxAttempts) {
            attempts++;

            if (totalWeight <= 0) break;

            LootItem selectedItem = rollItem(totalWeight);
            if (selectedItem == null) continue;

            // Уникаємо дублювання поки є альтернативи
            if (addedItems.contains(selectedItem.itemId()) &&
                    addedItems.size() < globalLootTable.items().size()) {
                continue;
            }

            ItemStack itemStack = createItemFromId(selectedItem.itemId());
            if (itemStack != null && itemStack.getType() != Material.BARRIER) {
                int amount = calculateAmount(selectedItem);
                itemStack.setAmount(amount);

                currentLoot.add(itemStack);
                addedItems.add(selectedItem.itemId());
                addedCount++;

                logger.fine("  Added: " + selectedItem.itemId() + " x" + amount);
            }
        }

        logger.fine("Successfully added " + addedCount + " items to vanilla structure");
    }

    /**
     * Випадково вибирає предмет за вагою
     */
    private LootItem rollItem(int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (LootItem item : globalLootTable.items()) {
            cumulative += item.weight();
            if (roll < cumulative) {
                return item;
            }
        }
        return null;
    }

    /**
     * Розраховує кількість предмета
     */
    private int calculateAmount(LootItem item) {
        int min = item.minAmount();
        int max = item.maxAmount();
        return (min == max) ? min : (min + random.nextInt(max - min + 1));
    }

    /**
     * Створює ItemStack з ID
     */
    private ItemStack createItemFromId(String itemId) {
        if (itemId.startsWith("potion:")) {
            return createPotion(itemId);
        }

        if (itemId.startsWith("recipe:")) {
            return createRecipeBook(itemId);
        }

        String actualId = itemId.startsWith("custom:") ? itemId.substring(7) : itemId;
        Optional<ItemStack> itemStackOptional = customItemService.createItemStack(actualId);

        if (itemStackOptional.isEmpty()) {
            logger.warning("Failed to create item: " + itemId);
            return null;
        }

        return itemStackOptional.get();
    }

    private ItemStack createPotion(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) {
                return null;
            }

            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);

            if (sequence < 0 || sequence > 9) {
                return null;
            }

            return potionManager.createPotionItem(pathway, Sequence.of(sequence));
        } catch (Exception e) {
            logger.warning("Failed to create potion: " + itemId + " - " + e.getMessage());
            return null;
        }
    }

    private ItemStack createRecipeBook(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) {
                return null;
            }

            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);

            if (sequence < 0 || sequence > 9) {
                return null;
            }

            PathwayPotions pathwayPotions = potionManager.getPotionsPathway(pathway).orElse(null);
            if (pathwayPotions == null) {
                return null;
            }

            return recipeBookFactory.createRecipeBook(pathwayPotions, sequence);
        } catch (Exception e) {
            logger.warning("Failed to create recipe book: " + itemId + " - " + e.getMessage());
            return null;
        }
    }
}