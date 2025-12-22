package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;

import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.valueobjects.Sequence;
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

import javax.annotation.Nullable;

/**
 * Listener for pathway potion consumption.
 * Handles beyonder creation and advancement through potions.
 */
public class PathwayPotionListener implements Listener {
    private final PotionManager potionManager;
    private final BeyonderService beyonderService;

    public PathwayPotionListener(PotionManager potionManager, BeyonderService beyonderService) {
        this.potionManager = potionManager;
        this.beyonderService = beyonderService;
    }

    @EventHandler
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();

        // Only handle potions
        if (item.getType() == Material.AIR || item.getType() != Material.POTION) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Player player = event.getPlayer();

        // Find matching pathway potion
        PotionInfo potionInfo = findMatchingPotion(item);
        if (potionInfo == null) {
            return;
        }

        // Get or validate beyonder
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            // First potion - create new beyonder
            handleFirstPotion(event, player, potionInfo);
        } else {
            // Advancement potion
            handleAdvancementPotion(event, player, beyonder, potionInfo);
        }
    }

    /**
     * Find pathway and sequence for consumed potion
     */
    @Nullable
    private PotionInfo findMatchingPotion(ItemStack item) {
        for (PathwayPotions pathwayPotions : potionManager.getPotions()) {
            for (int sequence = 0; sequence < 10; sequence++) {
                if (pathwayPotions.returnPotionForSequence(sequence).isSimilar(item)) {
                    return new PotionInfo(pathwayPotions.getPathway(), sequence);
                }
            }
        }
        return null;
    }

    /**
     * Handle first potion consumption (sequence 9)
     */
    private void handleFirstPotion(PlayerItemConsumeEvent event, Player player, PotionInfo potionInfo) {
        if (potionInfo.sequence != 9) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Ви повинні почати з послідовності 9!");
            return;
        }

        // Create new beyonder at sequence 9
        Beyonder beyonder = new Beyonder(
                player.getUniqueId(),
                Sequence.of(potionInfo.sequence),
                potionInfo.pathway
        );

        // Add to service (this also creates UI)
        beyonderService.createBeyonder(beyonder);

        // Show welcome message
        player.sendMessage(ChatColor.GREEN +
                "Вітаємо у світі Потойбічних, " + player.getDisplayName());

        // Apply effects
        applyPotionEffects(player);
    }

    /**
     * Handle advancement potion consumption
     */
    private void handleAdvancementPotion(
            PlayerItemConsumeEvent event,
            Player player,
            Beyonder beyonder,
            PotionInfo potionInfo
    ) {
        // Validate can consume this potion
        if (!beyonder.canConsumePotion(potionInfo.pathway, Sequence.of(potionInfo.sequence))) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Ви не можете споживати цей еліксір!");
            return;
        }

        // Advance beyonder
        beyonder.advance();
        beyonderService.updateBeyonder(beyonder);

        // Show advancement message
        player.sendMessage(ChatColor.GREEN +
                "Ви просунулися до послідовності " + beyonder.getSequenceLevel() + "!");

        // Apply effects
        applyPotionEffects(player);
    }

    /**
     * Apply visual and status effects after potion consumption
     */
    private void applyPotionEffects(Player player) {
        // Status effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 1));

        // Sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

        // Particle effects
        player.getWorld().spawnParticle(
                Particle.PORTAL,
                player.getLocation().add(0, 1, 0),
                50
        );
    }

    /**
     * Helper record for potion information
     */
    private record PotionInfo(Pathway pathway, int sequence) {
    }
}
