package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** ПКМ по Посереднику з предметом у руці → миттєва скупка за конфіг-прайсом. */
public class OrganizerClickListener implements Listener {

    private final OrganizerNpcService organizerNpc;
    private final GatheringService gatheringService;

    public OrganizerClickListener(OrganizerNpcService organizerNpc, GatheringService gatheringService) {
        this.organizerNpc = organizerNpc;
        this.gatheringService = gatheringService;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (!organizerNpc.isOrganizer(event.getNPC())) {
            return;
        }
        var player = event.getClicker();
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: «Покажи, що приніс — візьми річ у руку.»");
            return;
        }
        gatheringService.buybackFromHand(player);
    }
}
