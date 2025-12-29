package me.vangoo.infrastructure.structures;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.domain.valueobjects.StructureData;
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
    private final NamespacedKey lootTagKey;
    private final Random random = new Random();

    public LootGenerationService(Plugin plugin, CustomItemService customItemService) {
        this.plugin = plugin;
        this.customItemService = customItemService;
        this.lootTagKey = new NamespacedKey(plugin, "chest_tag");
    }

    public void processStructureLoot(StructureData data, Location center) {
        if (data.lootTables().isEmpty()) return;

        World world = center.getWorld();
        int radius = 32; // Можна винести в конфіг, якщо потрібно

        // Скануємо область навколо центру структури на наявність сундуків
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

        // Очищаємо тег, щоб не заповнювати повторно, і очищаємо інвентар
        container.remove(lootTagKey);
        chest.update();
        chest.getInventory().clear();

        populateInventory(chest, lootTable);
    }

    private void populateInventory(Chest chest, LootTableData lootTable) {
        int totalWeight = lootTable.items().stream().mapToInt(LootItem::weight).sum();
        int inventorySize = chest.getInventory().getSize();

        // Визначаємо кількість предметів для генерації (3-8 предметів)
        int itemCount = 3 + random.nextInt(6);
        itemCount = Math.min(itemCount, inventorySize);

        // Створюємо список доступних слотів
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) {
            availableSlots.add(i);
        }
        // Перемішуємо слоти для випадкового розподілу
        Collections.shuffle(availableSlots, random);

        // Заповнюємо випадкові слоти предметами
        for (int i = 0; i < itemCount; i++) {
            LootItem selectedItem = rollItem(lootTable, totalWeight);

            if (selectedItem != null) {
                ItemStack itemStack = getCustomItem(selectedItem.itemId());
                int amount = selectedItem.minAmount() + random.nextInt(selectedItem.maxAmount() - selectedItem.minAmount() + 1);
                itemStack.setAmount(amount);

                // Розміщуємо предмет у випадковий слот
                int slot = availableSlots.get(i);
                chest.getInventory().setItem(slot, itemStack);
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

    private ItemStack getCustomItem(String itemId) {
        Optional<ItemStack> itemStackOptional = customItemService.createItemStack(itemId);
        if (itemStackOptional.isEmpty()) {
            plugin.getLogger().warning("Failed to create custom item: " + itemId);
            return new ItemStack(Material.BARRIER);
        }
        return itemStackOptional.get();
    }

    // API для ручного встановлення тегів
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
