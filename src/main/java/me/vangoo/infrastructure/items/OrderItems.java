package me.vangoo.infrastructure.items;

import me.vangoo.application.services.CustomItemService;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Ідентифікація та створення предметів орденів. Шифроване послання — генеричний custom
 * item: воно не кодує орден (гравець обирає його сам у пікері вступу).
 *
 * <p>Талісмана тут більше нема: зв'язок з орденом — вкладка в меню Містичних Здібностей
 * ({@code AbilityMenu}), а не предмет. Членство не мусить бути фізичним об'єктом, який
 * можна загубити, вкрасти чи пред'явити як доказ — орден і так пам'ятає своїх.</p> */
public class OrderItems {

    public static final String CIPHER_ID = "order_cipher_message";

    private final CustomItemService customItemService;

    public OrderItems(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    public boolean isCipherMessage(ItemStack stack) {
        return hasId(stack, CIPHER_ID);
    }

    public Optional<ItemStack> createCipherMessage() {
        return customItemService.createItemStack(CIPHER_ID);
    }

    private boolean hasId(ItemStack stack, String id) {
        return customItemService.getCustomItem(stack)
                .map(item -> id.equals(item.id()))
                .orElse(false);
    }
}
