package me.vangoo.infrastructure.items;

import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Factory for creating ItemStacks from CustomItem definitions
 * Updated for Minecraft 1.21.8 - uses String customModelData only
 */
public class CustomItemFactory {
    private static final String NBT_CUSTOM_ITEM_KEY = "custom_item_id";

    /**
     * Create ItemStack from CustomItem definition
     */
    public ItemStack createItemStack(CustomItem item) {
        return createItemStack(item, 1);
    }

    /**
     * Create ItemStack from CustomItem definition with specified amount
     */
    public ItemStack createItemStack(CustomItem item, int amount) {
        ItemStack itemStack = new ItemStack(item.material(), amount);
        ItemMeta meta = itemStack.getItemMeta();

        if (meta == null) {
            return itemStack;
        }

        // Set display name
        meta.setDisplayName(item.displayName());

        // Set lore
        if (!item.lore().isEmpty()) {
            meta.setLore(item.lore());
        }

        // Set custom model data (String format using Paper DataComponent API)
        if (item.hasCustomModelData()) {
            // Використовуємо item.id() як рядковий ідентифікатор для custom model data.
            // Якщо у тебе є окрема властивість (наприклад item.customModelKey()), підстав її.
            String modelKey = item.customModelData();

            try {
                CustomModelDataComponent comp = meta.getCustomModelDataComponent();
                comp.setStrings(java.util.List.of(modelKey));
                meta.setCustomModelDataComponent(comp);
            } catch (Throwable t) {
            }
        }

        // Add glow effect
        if (item.glow()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Hide other attributes
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP
        );

        // Предмети на музичній пластинці стакаються до 1 у ванілі — вирівнюємо до 64, як було на
        // папері. Гейт на матеріал: книга рецептів (ENCHANTED_BOOK) мусить лишитись нестакованою.
        if (DiscItems.isDisc(item.material())) {
            DiscItems.applyStackSize(meta);
        }

        itemStack.setItemMeta(meta);

        // Add NBT tag with custom item ID
        NBTBuilder nbtBuilder = new NBTBuilder(itemStack);
        itemStack = nbtBuilder
                .setString(NBT_CUSTOM_ITEM_KEY, item.id())
                .build();

        // Пластинку не можна вставляти в програвач (інакше інгредієнт зник би в jukebox).
        DiscItems.stripJukeboxPlayable(itemStack);

        return itemStack;
    }

    /**
     * Check if ItemStack is a custom item
     */
    public boolean isCustomItem(@Nullable ItemStack itemStack) {
        // getItemMeta() == null (напр. AIR) → NBTBuilder кинув би виняток. Читання й так null-safe.
        if (itemStack == null || itemStack.getItemMeta() == null) {
            return false;
        }

        NBTBuilder nbtBuilder = new NBTBuilder(itemStack);
        return nbtBuilder.getString(itemStack, NBT_CUSTOM_ITEM_KEY).isPresent();
    }

    /**
     * Get custom item ID from ItemStack
     */
    public Optional<String> getCustomItemId(@Nullable ItemStack itemStack) {
        // getItemMeta() == null (напр. AIR) → NBTBuilder кинув би виняток. Читання й так null-safe.
        if (itemStack == null || itemStack.getItemMeta() == null) {
            return Optional.empty();
        }

        NBTBuilder nbtBuilder = new NBTBuilder(itemStack);
        return nbtBuilder.getString(itemStack, NBT_CUSTOM_ITEM_KEY);
    }
}