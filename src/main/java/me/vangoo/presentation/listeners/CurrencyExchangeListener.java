package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.items.CurrencyCodec;
import me.vangoo.infrastructure.ui.ConfirmationMenu;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Ручний розмін валюти (1 фунт = 20 коппетів), з підтвердженням через {@link ConfirmationMenu}:
 * <ul>
 *   <li><b>Фунт → коппети:</b> звичайний ПКМ по фунту в руці розмінює 1 фунт на 20 коппетів
 *       (щоб не красти інтеракції блоків — лише в повітря або по неінтерактивному блоку).</li>
 *   <li><b>Коппети → фунт:</b> Shift+ПКМ по коппетах у руці об'єднує всі повні двадцятки стаку
 *       в руці у фунти; залишок (&lt; 20) лишається.</li>
 * </ul>
 * Захист від дюпів: предмети створюються лише через {@link CurrencyCodec}, стак у руці зменшується
 * рівно на витрачене, подія скасовується, а фактична конвертація на підтвердженні ПЕРЕЧИТУЄ руку
 * (стан на момент кліку — лише прев'ю).
 */
public class CurrencyExchangeListener implements Listener {

    private static final int COPPETS_PER_POUND = 20;

    private final CurrencyCodec codec;
    private final ConfirmationMenu confirmationMenu;

    public CurrencyExchangeListener(CurrencyCodec codec, ConfirmationMenu confirmationMenu) {
        this.codec = codec;
        this.confirmationMenu = confirmationMenu;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // подія стріляє двічі (обидві руки) — беремо лише головну
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean pound = codec.isPound(hand);
        boolean coppet = codec.isCoppet(hand);
        if (!pound && !coppet) {
            return;
        }

        // Коппети → фунт: Shift+ПКМ (Shift глушить GUI блоків, тож блок не крадемо).
        if (coppet && player.isSneaking()) {
            event.setCancelled(true);
            promptMergeCoppets(player, hand.getAmount());
            return;
        }
        // Фунт → коппети: звичайний ПКМ, але не перехоплюємо інтерактивні блоки (скриня/двері тощо).
        if (pound && !player.isSneaking()) {
            if (action == Action.RIGHT_CLICK_BLOCK && isInteractable(event.getClickedBlock())) {
                return;
            }
            event.setCancelled(true);
            promptBreakPound(player);
        }
    }

    private void promptBreakPound(Player player) {
        confirmationMenu.open(player,
                codec.createPounds(1),
                codec.createCoppets(COPPETS_PER_POUND),
                "Розмін фунта",
                () -> {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (!codec.isPound(hand) || hand.getAmount() < 1) {
                        return; // рука змінилась поки відкрите підтвердження — скасовуємо
                    }
                    decrementMainHand(player, hand, 1);
                    giveOrDrop(player, codec.createCoppets(COPPETS_PER_POUND));
                    feedback(player, ChatColor.YELLOW + "Розміняно 1 фунт на "
                            + COPPETS_PER_POUND + " коппетів");
                });
    }

    private void promptMergeCoppets(Player player, int amountAtClick) {
        int pounds = amountAtClick / COPPETS_PER_POUND;
        if (pounds <= 0) {
            feedback(player, ChatColor.RED + "Потрібно щонайменше "
                    + COPPETS_PER_POUND + " коппетів для фунта");
            return;
        }
        confirmationMenu.open(player,
                codec.createCoppets(pounds * COPPETS_PER_POUND),
                codec.createPounds(pounds),
                "Об'єднання коппетів",
                () -> {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (!codec.isCoppet(hand)) {
                        return; // рука змінилась поки відкрите підтвердження
                    }
                    int poundsNow = hand.getAmount() / COPPETS_PER_POUND;
                    if (poundsNow <= 0) {
                        return;
                    }
                    int spent = poundsNow * COPPETS_PER_POUND;
                    decrementMainHand(player, hand, spent);
                    giveOrDrop(player, codec.createPounds(poundsNow));
                    feedback(player, ChatColor.GOLD + "Об'єднано " + spent + " коппетів у "
                            + poundsNow + " фунт(ів)");
                });
    }

    /** Зменшує стак у головній руці на {@code amount}; спорожнілий слот очищається. */
    private void decrementMainHand(Player player, ItemStack hand, int amount) {
        int left = hand.getAmount() - amount;
        if (left <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            player.getInventory().setItemInMainHand(hand);
        }
    }

    /** Додає предмет в інвентар; надлишок (повний інвентар) падає під ноги. */
    private void giveOrDrop(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    private void feedback(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    private boolean isInteractable(Block block) {
        return block != null && block.getType().isInteractable();
    }
}
