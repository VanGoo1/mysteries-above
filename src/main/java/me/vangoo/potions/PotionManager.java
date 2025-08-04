package me.vangoo.potions;

import me.vangoo.LotmPlugin;
import me.vangoo.beyonders.Beyonder;
import me.vangoo.beyonders.BeyonderManager;
import me.vangoo.implementation.ErrorPathway.ErrorPotions;
import me.vangoo.pathways.Pathway;
import me.vangoo.pathways.PathwayManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PotionManager implements Listener {
    private final List<PathwayPotions> potions;
    private final PathwayManager pathwayManager;
    LotmPlugin plugin;

    public PotionManager(PathwayManager pathwayManager, LotmPlugin plugin) {
        this.pathwayManager = pathwayManager;
        this.potions = new ArrayList<>();
        this.plugin = plugin;
        initializePotions();
    }

    private void initializePotions() {
        potions.add(new ErrorPotions(pathwayManager.getPathway("Error"), Color.fromRGB(11, 11, 69)));
    }

    public List<PathwayPotions> getPotions() {
        return potions;
    }

    @EventHandler
    public void onPotionInteract(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() == Material.AIR)
            return;
        if (item.getType() != Material.POTION)
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Player player = event.getPlayer();
        Pathway pathway = null;
        int sequence = -1;

        for (PathwayPotions p : potions) {
            for (int i = 0; i < 10; i++) {
                if (p.returnPotionForSequence(i).isSimilar(item)) {
                    pathway = p.pathway;
                    sequence = i;
                }
            }
        }
        if (pathway == null || sequence == -1) return;
        Beyonder beyonder = plugin.getBeyonderManager().GetBeyonder(player.getUniqueId());
        if (beyonder == null) {
            beyonder = new Beyonder(player.getUniqueId());
        }
        if (!canConsumePotion(beyonder, pathway, sequence)) {
            event.setCancelled(true);
            return;
        }
        applyPotionEffect(player, beyonder, sequence, pathway, plugin.getBeyonderManager());
    }

    private boolean canConsumePotion(Beyonder beyonder, Pathway pathway, int sequence) {
        // Якщо гравець не потусторонній
        if (beyonder.getSequence() == -1) {
            // Може стати потустороннім тільки з зілля послідовності 9
            return sequence == 9;
        }
        // Якщо гравець вже потусторонній
        // Перевіряємо чи той самий шлях та чи тієї ж групи
        if (beyonder.getPathway().getGroup() != pathway.getGroup()) {
            return false;
        }

        // Перевіряємо чи наступна послідовність (у зворотному порядку)
        if (beyonder.getSequence() != sequence + 1) {
            return false; // Не та послідовність
        }

        // Перевіряємо чи засвоєння 100%
        return beyonder.getMastery() >= 100;
    }

    private void applyPotionEffect(Player player, Beyonder beyonder,
                                   int sequence, Pathway pathway, BeyonderManager beyonderManager) {
        if (beyonder.getSequence() == -1) {
            player.sendMessage(ChatColor.GREEN + "Вітаємо у світі Потойбічних, " + player.getDisplayName());
            beyonder.setSequence(sequence);
            beyonder.setPathway(pathway);
            beyonderManager.AddBeyonder(beyonder);
            beyonder.setMaxSpirituality(100);
            beyonderManager.createSpiritualityBar(player, beyonder);

        } else {
            beyonder.advance();
            player.sendMessage("§aВи просунулися до послідовності " + beyonder.getSequence() + "!");
        }

    }
}
