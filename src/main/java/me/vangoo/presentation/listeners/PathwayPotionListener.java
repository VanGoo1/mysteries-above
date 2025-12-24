package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;

import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;

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
        if (!potionManager.isPathwayPotion(item)) {
            return;
        }


        Player player = event.getPlayer();

        Optional<String> pathwayNameOpt = potionManager.getPathwayFromItem(item);
        Optional<Integer> sequenceOpt = potionManager.getSequenceFromItem(item);

        if (pathwayNameOpt.isEmpty() || sequenceOpt.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Пошкоджене зілля!");
            return;
        }

        String pathwayName = pathwayNameOpt.get();
        int sequence = sequenceOpt.get();

        Pathway pathway = potionManager.getPotionsPathway(pathwayName)
                .map(p -> p.getPathway())
                .orElse(null);

        if (pathway == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Невідомий шлях!");
            return;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            handleFirstPotion(event, player, pathway, sequence);
        } else {
            handleAdvancementPotion(event, player, beyonder, pathway, sequence);
        }

    }

    /**
     * Handle first potion consumption (sequence 9)
     */
    private void handleFirstPotion(PlayerItemConsumeEvent event, Player player, Pathway pathway, int sequence) {
        if (sequence != 9) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GRAY + "Краще почати з послідовності 9...");
            return;
        }

        Beyonder beyonder = new Beyonder(
                player.getUniqueId(),
                Sequence.of(sequence),
                pathway
        );

        // Add to service (this also creates UI)
        beyonderService.createBeyonder(beyonder);

        player.sendMessage(ChatColor.GREEN +
                "Вітаємо у світі Потойбічних, " + player.getDisplayName());
        player.sendMessage(ChatColor.GRAY + "Шлях: " + ChatColor.YELLOW + pathway.getName());

        // Apply effects
        applyPotionEffects(player);
    }

    /**
     * Handle advancement potion consumption
     */
    private void handleAdvancementPotion(
            PlayerItemConsumeEvent event, Player player,
            Beyonder beyonder, Pathway pathway, int sequence
    ) {
        if (!beyonder.canConsumePotion(pathway, Sequence.of(sequence))) {
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
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 1));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        player.getWorld().spawnParticle(
                Particle.PORTAL,
                player.getLocation().add(0, 1, 0),
                50
        );
    }
}