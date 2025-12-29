package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.LootTable;
import me.vangoo.domain.valueobjects.Structure;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;

/**
 * Application Service: Generates loot for structure chests
 */
public class LootGenerationService {
    private static final Logger LOGGER = Logger.getLogger(LootGenerationService.class.getName());
    private static final Random RANDOM = new Random();

    private final CustomItemService customItemService;

    public LootGenerationService(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    /**
     * Fill chest with loot from table
     */
    public void fillChest(Block block, LootTable lootTable) {
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }

        Inventory inventory = chest.getInventory();
        inventory.clear();

        List<ItemStack> items = generateLoot(lootTable);
        placeItemsRandomly(inventory, items);

        chest.update();
    }

    /**
     * Generate loot items based on table weights
     */
    private List<ItemStack> generateLoot(LootTable lootTable) {
        List<ItemStack> result = new ArrayList<>();

        if (lootTable.items().isEmpty()) {
            return result;
        }

        // Calculate total weight
        int totalWeight = lootTable.items().stream()
                .mapToInt(LootTable.LootEntry::weight)
                .sum();

        // Generate 1-5 items
        int itemCount = 1 + RANDOM.nextInt(5);

        for (int i = 0; i < itemCount; i++) {
            LootTable.LootEntry entry = selectWeightedEntry(lootTable.items(), totalWeight);
            if (entry == null) continue;

            Optional<ItemStack> item = customItemService.createItemStack(
                    entry.itemId(),
                    entry.getRandomAmount()
            );

            item.ifPresent(result::add);
        }

        return result;
    }

    /**
     * Select entry based on weight
     */
    private LootTable.LootEntry selectWeightedEntry(
            List<LootTable.LootEntry> entries,
            int totalWeight
    ) {
        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (LootTable.LootEntry entry : entries) {
            current += entry.weight();
            if (roll < current) {
                return entry;
            }
        }

        return entries.get(0);
    }

    /**
     * Place items randomly in chest inventory
     */
    private void placeItemsRandomly(Inventory inventory, List<ItemStack> items) {
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            availableSlots.add(i);
        }

        Collections.shuffle(availableSlots);

        for (int i = 0; i < Math.min(items.size(), availableSlots.size()); i++) {
            inventory.setItem(availableSlots.get(i), items.get(i));
        }
    }
}