package me.vangoo.infrastructure.structures;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.StructureData;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class LootGenerationService {
    private final Plugin plugin;
    private final CustomItemService customItemService;
    private final PotionManager potionManager;
    private final RecipeBookFactory recipeBookFactory;
    private final NamespacedKey lootTagKey;
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
        this.lootTagKey = new NamespacedKey(plugin, "chest_tag");
    }

    public void processStructureLoot(StructureData data, Location center) {
        if (data.lootTables().isEmpty()) return;

        World world = center.getWorld();
        int radius = 32;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    assert world != null;
                    Block block = world.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z
                    );

                    if (block.getState() instanceof Chest chest) {
                        fillChestIfTagged(chest, data);
                    }
                }
            }
        }
    }

    private void fillChestIfTagged(Chest chest, StructureData data) {
        var container = chest.getPersistentDataContainer();

        if (!container.has(lootTagKey, PersistentDataType.STRING)) {
            return;
        }

        String tag = container.get(lootTagKey, PersistentDataType.STRING);
        LootTableData lootTable = data.lootTables().get(tag);

        if (lootTable == null) {
            plugin.getLogger().warning("No loot table found for tag: '" + tag + "' in structure: " + data.id());
            return;
        }

        container.remove(lootTagKey);
        chest.update();
        chest.getInventory().clear();

        populateInventory(chest, lootTable);
    }

    private void populateInventory(Chest chest, LootTableData lootTable) {
        int totalWeight = lootTable.items().stream().mapToInt(LootItem::weight).sum();
        int inventorySize = chest.getInventory().getSize();

        // Використовуємо min/max з lootTable, якщо вони є
        int minItems = lootTable.minItems();
        int maxItems = lootTable.maxItems();

        int itemCount = (minItems == maxItems)
                ? minItems
                : (minItems + random.nextInt(maxItems - minItems + 1));
        itemCount = Math.min(itemCount, inventorySize);

        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) availableSlots.add(i);
        Collections.shuffle(availableSlots, random);

        Set<String> addedItems = new HashSet<>();
        int successfullyAdded = 0;
        int attempts = 0;
        int maxAttempts = Math.max(itemCount * 5, 50); // захист від зациклення

        while (successfullyAdded < itemCount && attempts < maxAttempts) {
            attempts++;

            // Якщо сумарна вага 0 — нічого не крутити
            if (totalWeight <= 0) break;

            LootItem selectedItem = rollItem(lootTable, totalWeight);
            if (selectedItem == null) continue;

            // уникати дублювань поки є альтернативи
            if (addedItems.contains(selectedItem.itemId()) && addedItems.size() < lootTable.items().size()) {
                continue;
            }

            ItemStack itemStack = createItemFromId(selectedItem.itemId());
            if (itemStack != null) {
                int amountMin = selectedItem.minAmount();
                int amountMax = selectedItem.maxAmount();
                int amount = (amountMin == amountMax)
                        ? amountMin
                        : (amountMin + random.nextInt(amountMax - amountMin + 1));
                itemStack.setAmount(Math.max(1, amount));

                if (availableSlots.isEmpty()) break;
                int slot = availableSlots.remove(0);
                chest.getInventory().setItem(slot, itemStack);

                addedItems.add(selectedItem.itemId());
                successfullyAdded++;
            }
        }
    }


    private LootItem rollItem(LootTableData lootTable, int totalWeight) {
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
     * Create item from ID with support for:
     * - Custom items: "item_id" or "custom:item_id"
     * - Potions: "potion:pathway:sequence"
     * - Recipe books: "recipe:pathway:sequence"
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
            plugin.getLogger().warning("Failed to create item: " + itemId);
            return new ItemStack(Material.BARRIER);
        }

        return itemStackOptional.get();
    }

    private ItemStack createPotion(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) {
                plugin.getLogger().warning("Invalid potion format: " + itemId + " (expected: potion:pathway:sequence)");
                return new ItemStack(Material.BARRIER);
            }

            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);

            if (sequence < 0 || sequence > 9) {
                plugin.getLogger().warning("Invalid sequence in potion: " + itemId + " (must be 0-9)");
                return new ItemStack(Material.BARRIER);
            }

            return potionManager.createPotionItem(pathway, Sequence.of(sequence));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create potion: " + itemId + " - " + e.getMessage());
            return new ItemStack(Material.BARRIER);
        }
    }

    private ItemStack createRecipeBook(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 3) {
                plugin.getLogger().warning("Invalid recipe format: " + itemId + " (expected: recipe:pathway:sequence)");
                return new ItemStack(Material.BARRIER);
            }

            String pathway = parts[1];
            int sequence = Integer.parseInt(parts[2]);

            if (sequence < 0 || sequence > 9) {
                plugin.getLogger().warning("Invalid sequence in recipe: " + itemId + " (must be 0-9)");
                return new ItemStack(Material.BARRIER);
            }

            PathwayPotions pathwayPotions = potionManager.getPotionsPathway(pathway).orElse(null);
            if (pathwayPotions == null) {
                plugin.getLogger().warning("Unknown pathway in recipe: " + pathway);
                return new ItemStack(Material.BARRIER);
            }

            return recipeBookFactory.createRecipeBook(pathwayPotions, sequence);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create recipe book: " + itemId + " - " + e.getMessage());
            return new ItemStack(Material.BARRIER);
        }
    }

    public void setChestTag(Chest chest, String tag) {
        chest.getPersistentDataContainer().set(lootTagKey, PersistentDataType.STRING, tag);
        chest.update();
    }

    public String getChestTag(Chest chest) {
        if (!chest.getPersistentDataContainer().has(lootTagKey, PersistentDataType.STRING)) {
            return null;
        }
        return chest.getPersistentDataContainer().get(lootTagKey, PersistentDataType.STRING);
    }
}