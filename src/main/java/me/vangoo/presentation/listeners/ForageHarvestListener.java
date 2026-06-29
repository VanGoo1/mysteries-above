package me.vangoo.presentation.listeners;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.infrastructure.forage.ForageNodeCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Збір ноди фореджу: ПКМ по Interaction-хітбоксу -> інгредієнт падає на землю, нода прибирається. */
public class ForageHarvestListener implements Listener {

    private final ForageNodeCodec codec;
    private final CustomItemService customItemService;
    private final Plugin plugin;

    public ForageHarvestListener(ForageNodeCodec codec, CustomItemService customItemService, Plugin plugin) {
        this.codec = codec;
        this.customItemService = customItemService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onForageInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // ПКМ шле подію двічі (рука+оффхенд)
        Entity target = event.getRightClicked();
        if (!codec.isForageNode(target)) return;
        event.setCancelled(true);
        harvest(target);
    }

    private void harvest(Entity clicked) {
        Optional<String> ingredient = codec.readIngredient(clicked);
        Location loc = clicked.getLocation();
        Optional<ItemStack> stack = ingredient.flatMap(customItemService::createItemStack);
        removePair(clicked);
        if (stack.isEmpty()) {
            plugin.getLogger().warning("Forage harvest: could not resolve ingredient item for "
                    + ingredient.orElse("<none>"));
            return;
        }
        if (loc.getWorld() == null) return;
        loc.getWorld().dropItem(loc, stack.get());
        loc.getWorld().playSound(loc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.2f);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0.0);
    }

    private void removePair(Entity clicked) {
        Optional<UUID> partner = codec.readPartner(clicked);
        partner.ifPresent(id -> {
            Entity p = Bukkit.getEntity(id);
            if (p != null) p.remove();
        });
        clicked.remove();
    }
}
