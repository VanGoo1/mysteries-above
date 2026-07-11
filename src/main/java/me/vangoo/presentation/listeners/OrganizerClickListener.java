package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import me.vangoo.infrastructure.ui.ConfirmationMenu;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

/** ПКМ по Посереднику з предметом у руці → підтвердження скупки за конфіг-прайсом. */
public class OrganizerClickListener implements Listener {

    private final OrganizerNpcService organizerNpc;
    private final GatheringService gatheringService;
    private final ConfirmationMenu confirmationMenu;

    public OrganizerClickListener(OrganizerNpcService organizerNpc, GatheringService gatheringService,
                                  ConfirmationMenu confirmationMenu) {
        this.organizerNpc = organizerNpc;
        this.gatheringService = gatheringService;
        this.confirmationMenu = confirmationMenu;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (!organizerNpc.isOrganizer(event.getNPC())) {
            return;
        }
        Player player = event.getClicker();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            organizerSay(player, "Покажи, що приніс — візьми річ у руку.");
            return;
        }
        Optional<PoundMoney> payout = gatheringService.buybackPayout(hand);
        if (payout.isEmpty()) {
            organizerSay(player, "За таке я не дам і коппета.");
            return;
        }
        ItemStack coins = coinLabel(payout.get());
        confirmationMenu.open(player, hand.clone(), coins, "🕯 Скупка", () -> gatheringService.buybackFromHand(player));
    }

    /** Репліка Посередника — в action bar (як його доповідь), а не в чат. */
    private void organizerSay(Player player, String line) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "Посередник: «" + line + "»"));
    }

    private ItemStack coinLabel(PoundMoney money) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + money.format());
        meta.setLore(List.of());
        item.setItemMeta(meta);
        return item;
    }
}
