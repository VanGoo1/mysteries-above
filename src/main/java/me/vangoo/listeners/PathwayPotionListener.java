package me.vangoo.listeners;

import me.vangoo.domain.Beyonder;
import me.vangoo.domain.Pathway;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.managers.BeyonderManager;
import me.vangoo.managers.PotionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PathwayPotionListener implements Listener {

    private final PotionManager potionManager;
    private final BeyonderManager beyonderManager;

    public PathwayPotionListener(PotionManager potionManager, BeyonderManager beyonderManager) {
        this.potionManager = potionManager;
        this.beyonderManager = beyonderManager;
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

        for (PathwayPotions p : potionManager.getPotions()) {
            for (int i = 0; i < 10; i++) {
                if (p.returnPotionForSequence(i).isSimilar(item)) {
                    pathway = p.getPathway();
                    sequence = i;
                }
            }
        }
        if (pathway == null) return;
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());

        if (!potionManager.canConsumePotion(beyonder, pathway, sequence)) {
            event.setCancelled(true);
            return;
        }
        beyonder = new Beyonder(player.getUniqueId(), sequence, pathway);

        applyPotionEffect(player, beyonder);
    }

    private void applyPotionEffect(Player player, Beyonder beyonder) {
        if (beyonder.getSequence() == 9) {
            player.sendMessage(ChatColor.GREEN + "Вітаємо у світі Потойбічних, " + player.getDisplayName());
            beyonder.setMaxSpirituality(100);
            beyonder.setSpirituality(beyonder.getMaxSpirituality());
            beyonderManager.AddBeyonder(beyonder);
            beyonderManager.createSpiritualityBar(player, beyonder);
        } else {
            beyonder.advance();
            beyonderManager.updateBeyonder(beyonder);
            player.sendMessage("§aВи просунулися до послідовності " + beyonder.getSequence() + "!");
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 1));

        // Звукові ефекти
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

        // Частинки
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50);
    }
}
