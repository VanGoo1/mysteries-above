package me.vangoo.application.services;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.infrastructure.items.CustomItemRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Application Service: Manages custom items
 */
public class CustomItemService implements IItemResolver {
    private final CustomItemRegistry registry;

    public CustomItemService(CustomItemRegistry registry) {
        this.registry = registry;
    }

    /**
     * Get custom item by ID
     */
    public Optional<CustomItem> getItem(String id) {
        return registry.getItem(id);
    }

    /**
     * Get all custom items
     */
    public Collection<CustomItem> getAllItems() {
        return registry.getAllItems();
    }

    /**
     * Create ItemStack from item ID
     */
    public Optional<ItemStack> createItemStack(String id) {
        return registry.createItemStack(id);
    }

    /**
     * Create ItemStack from item ID with amount
     */
    public Optional<ItemStack> createItemStack(String id, int amount) {
        return registry.createItemStack(id, amount);
    }

    /**
     * Get custom item from ItemStack
     */
    public Optional<CustomItem> getCustomItem(ItemStack itemStack) {
        return registry.getCustomItem(itemStack);
    }

    /**
     * Check if ItemStack is a custom item
     */
    public boolean isCustomItem(ItemStack itemStack) {
        return registry.isCustomItem(itemStack);
    }

    /**
     * Check if item ID exists
     */
    public boolean hasItem(String id) {
        return registry.hasItem(id);
    }

    /**
     * Get statistics about custom items
     */
    public Map<String, Object> getStatistics() {
        return registry.getStatistics();
    }
}