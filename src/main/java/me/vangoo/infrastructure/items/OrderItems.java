package me.vangoo.infrastructure.items;

import me.vangoo.application.services.CustomItemService;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Ідентифікація та створення предметів орденів. Обидва — генеричні custom items:
 * талісман не кодує орден (членство сервіс знає за UUID власника). */
public class OrderItems {

    public static final String CIPHER_ID = "order_cipher_message";
    public static final String TALISMAN_ID = "order_talisman";

    private final CustomItemService customItemService;

    public OrderItems(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    public boolean isCipherMessage(ItemStack stack) {
        return hasId(stack, CIPHER_ID);
    }

    public boolean isTalisman(ItemStack stack) {
        return hasId(stack, TALISMAN_ID);
    }

    public Optional<ItemStack> createCipherMessage() {
        return customItemService.createItemStack(CIPHER_ID);
    }

    public Optional<ItemStack> createTalisman() {
        return customItemService.createItemStack(TALISMAN_ID);
    }

    private boolean hasId(ItemStack stack, String id) {
        return customItemService.getCustomItem(stack)
                .map(item -> id.equals(item.id()))
                .orElse(false);
    }
}
