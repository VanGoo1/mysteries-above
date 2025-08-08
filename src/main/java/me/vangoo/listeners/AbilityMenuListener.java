package me.vangoo.listeners;

import me.vangoo.abilities.AbilityManager;
import me.vangoo.beyonders.Beyonder;
import me.vangoo.beyonders.BeyonderManager;
import me.vangoo.utils.AbilityMenu;
import me.vangoo.utils.NBTBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;


public class AbilityMenuListener implements Listener {

    AbilityMenu abilityMenu;
    BeyonderManager beyonderManager;
    AbilityManager abilityManager;

    public AbilityMenuListener(AbilityMenu abilityMenu, BeyonderManager beyonderManager, AbilityManager abilityManager) {
        this.abilityMenu = abilityMenu;
        this.beyonderManager = beyonderManager;
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;
        if (beyonder.getSequence() != -1) {
            abilityMenu.giveAbilityMenuItemToPlayer(player);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.AIR) return;

        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;

        if (abilityMenu.isAbilityMenu(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getCursor() != null || event.getCursor().getType() != Material.AIR) {
                player.getWorld().dropItemNaturally(player.getLocation(), event.getCursor());
                player.setItemOnCursor(new ItemStack(Material.AIR));
            }
            abilityMenu.openMenu(player, beyonder);
        } else if (abilityMenu.isAbilityItemInMenu(event.getCurrentItem())) {
            event.setCancelled(true);
            ItemStack abilityItem = new NBTBuilder(event.getCurrentItem()).remove("isInAbilityMenu").build();
            if (player.getInventory().contains(abilityItem)) {
                return;
            }
            player.getInventory().addItem(abilityItem);
        }
    }

    @EventHandler
    public void onItemAbilityClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.AIR) return;

        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;
        if (abilityManager.GetAbilityFromItem(event.getCurrentItem(), beyonder) != null) {
            if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onAbilityItemPlace(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCursor() == null) return;
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;
        if (abilityManager.GetAbilityFromItem(event.getCursor(), beyonder) == null)
            return;

        if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE || event.getAction() == InventoryAction.PLACE_SOME) {
            if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }


    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (abilityMenu.isAbilityMenu(item)) event.setCancelled(true);
        Beyonder beyonder = beyonderManager.GetBeyonder(event.getPlayer().getUniqueId());
        if (beyonder == null) return;

        if (abilityManager.GetAbilityFromItem(event.getItemDrop().getItemStack(), beyonder) != null) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        Beyonder beyonder = beyonderManager.GetBeyonder(event.getEntity().getUniqueId());
        if (beyonder == null) return;
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (abilityMenu.isAbilityMenu(item)) {
                iterator.remove();
            } else if (abilityManager.GetAbilityFromItem(item, beyonder) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());
        if (beyonder == null) return;
        if (beyonder.getSequence() != -1) {
            abilityMenu.giveAbilityMenuItemToPlayer(player);
        }
    }
}
