package me.vangoo.infrastructure.items;

import me.vangoo.domain.valueobjects.CustomItem;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CustomItemRegistry {
    private final Map<String, CustomItem> items;
    private final CustomItemFactory itemFactory;

    public CustomItemRegistry(CustomItemFactory itemFactory) {
        this.items = new HashMap<>();
        this.itemFactory = itemFactory;
    }

    /**
     * Register a custom item
     */
    public void register(CustomItem item) {
        items.put(item.id(), item);
    }

    /**
     * Register multiple custom items
     */
    public void registerAll(Map<String, CustomItem> items) {
        this.items.putAll(items);
    }

    /**
     * Get custom item by ID
     */
    public Optional<CustomItem> getItem(String id) {
        return Optional.ofNullable(items.get(id));
    }

    /**
     * Get all custom items
     */
    public Collection<CustomItem> getAllItems() {
        return Collections.unmodifiableCollection(items.values());
    }


    /**
     * Get all item IDs
     */
    public Set<String> getItemIds() {
        return Collections.unmodifiableSet(items.keySet());
    }

    /**
     * Check if item ID exists
     */
    public boolean hasItem(String id) {
        return items.containsKey(id);
    }

    /**
     * Create ItemStack from custom item ID
     */
    public Optional<ItemStack> createItemStack(String id) {
        return getItem(id).map(itemFactory::createItemStack);
    }

    /**
     * Create ItemStack from custom item ID with amount
     */
    public Optional<ItemStack> createItemStack(String id, int amount) {
        return getItem(id).map(item -> itemFactory.createItemStack(item, amount));
    }

    /**
     * Get custom item from ItemStack
     */
    public Optional<CustomItem> getCustomItem(ItemStack itemStack) {
        return itemFactory.getCustomItemId(itemStack)
                .flatMap(this::getItem);
    }

    /**
     * Check if ItemStack is a custom item
     */
    public boolean isCustomItem(ItemStack itemStack) {
        return itemFactory.isCustomItem(itemStack);
    }

    /**
     * Clear all registered items
     */
    public void clear() {
        items.clear();
    }

    /**
     * Get registry statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalItems", items.size());

        return stats;
    }
}