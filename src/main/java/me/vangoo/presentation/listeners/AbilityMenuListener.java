package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.ui.AbilityMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * Listener for ability menu interactions.
 * Updated to work with new triumph-gui based menu system.
 */
public class AbilityMenuListener implements Listener {
    private final AbilityMenu abilityMenu;
    private final BeyonderService beyonderService;
    private final AbilityItemFactory abilityItemFactory;
    private final Logger logger;

    public AbilityMenuListener(
            AbilityMenu abilityMenu,
            BeyonderService beyonderService,
            AbilityItemFactory abilityItemFactory,
            Logger logger
    ) {
        this.abilityMenu = abilityMenu;
        this.beyonderService = beyonderService;
        this.abilityItemFactory = abilityItemFactory;
        this.logger = logger;
    }

    /**
     * Видає предмет меню при вході гравця
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        abilityMenu.giveAbilityMenuItemToPlayer(player, beyonder);
    }

    /**
     * Обробляє клік в інвентарі на предмет меню або предмет здібності
     */
    @EventHandler
    public void onMenuItemClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        // Перевірка предметів
        boolean currentIsMenu = currentItem != null && abilityMenu.isAbilityMenu(currentItem);
        boolean cursorIsMenu = cursor != null && abilityMenu.isAbilityMenu(cursor);
        boolean currentIsAbility = currentItem != null &&
                abilityItemFactory.isAbilityItem(currentItem, beyonder);
        boolean cursorIsAbility = cursor != null &&
                abilityItemFactory.isAbilityItem(cursor, beyonder);

        assert currentItem != null;
        if (currentItem.getType() == Material.BUNDLE) {
            if (cursorIsMenu || cursorIsAbility) {
                event.setCancelled(true);
                return;
            }
        }

        // 2. Гравець клікає мішком по здібності/меню (намагається "зачерпнути" предмет)
        assert cursor != null;
        if (cursor.getType() == Material.BUNDLE) {
            if (currentIsMenu || currentIsAbility) {
                event.setCancelled(true);
                return;
            }
        }

        // Обробка кліку на предмет меню (відкриття меню)
        if (currentIsMenu) {
            handleMenuItemClick(event, player, beyonder);
            return;
        }

        // Якщо це не меню і не здібність - пропускаємо
        if (!cursorIsMenu && !currentIsAbility && !cursorIsAbility) {
            return;
        }

        // Заборона в не-плеєрських інвентарях
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            if (currentIsMenu || currentIsAbility) {
                event.setCancelled(true);
                return;
            }
        }

        // Заборона розміщення в crafting слоти
        if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            if (cursorIsMenu || cursorIsAbility) {
                event.setCancelled(true);
                return;
            }
        }

        // Заборона в offhand (slot 40)
        if (event.getSlot() == 40) {
            if (cursorIsMenu || cursorIsAbility) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Обробляє клік на предмет меню (відкриває меню)
     */
    private void handleMenuItemClick(InventoryClickEvent event, Player player, Beyonder beyonder) {
        event.setCancelled(true);

        // Скидаємо предмет з курсору
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            player.getWorld().dropItemNaturally(player.getLocation(), cursor);
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }

        // Відкриваємо меню
        abilityMenu.openMenu(player, beyonder);
    }

    /**
     * Заборона перемикання предметів меню/здібностей через hotbar swap
     */
    @EventHandler
    public void onHotbarSwap(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            return;
        }

        // Заборона SWAP_WITH_CURSOR для offhand
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            ItemStack cursor = event.getCursor();
            if (cursor != null &&
                    (abilityItemFactory.isAbilityItem(cursor, beyonder) ||
                            abilityMenu.isAbilityMenu(cursor))) {
                if (event.getSlot() == 40) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED +
                            "Не можна переміщувати здібності в ліву руку");
                }
            }
        }

        // Заборона hotbar swap через цифри
        if (event.getAction() == InventoryAction.HOTBAR_SWAP ||
                event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {

            int hotbarButton = event.getHotbarButton();
            if (hotbarButton != -1) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);

                if (hotbarItem != null &&
                        (abilityItemFactory.isAbilityItem(hotbarItem, beyonder) ||
                                abilityMenu.isAbilityMenu(hotbarItem))) {
                    if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    /**
     * Заборона swap через F
     */
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        boolean mainIsSpecial = mainHand != null &&
                (abilityItemFactory.isAbilityItem(mainHand, beyonder) ||
                        abilityMenu.isAbilityMenu(mainHand));
        boolean offIsSpecial = offHand != null &&
                (abilityItemFactory.isAbilityItem(offHand, beyonder) ||
                        abilityMenu.isAbilityMenu(offHand));

        if (mainIsSpecial || offIsSpecial) {
            if (player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Заборона розміщення в рамки
     */
    @EventHandler
    public void onPlaceInItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame itemFrame)) {
            return;
        }

        if (itemFrame.getItem().getType() != Material.AIR) {
            return;
        }

        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        if (offHandItem.getType() != Material.AIR) {
            if (abilityItemFactory.isAbilityItem(offHandItem, beyonder) ||
                    abilityMenu.isAbilityMenu(offHandItem)) {
                event.setCancelled(true);
                return;
            }
        }

        if (mainHandItem.getType() != Material.AIR) {
            if (abilityItemFactory.isAbilityItem(mainHandItem, beyonder) ||
                    abilityMenu.isAbilityMenu(mainHandItem)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Заборона викидання предмета меню
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        // Заборона викидання меню
        if (abilityMenu.isAbilityMenu(item)) {
            event.setCancelled(true);
            return;
        }

        // Видалення викинутих предметів здібностей
        Beyonder beyonder = beyonderService.getBeyonder(event.getPlayer().getUniqueId());
        if (beyonder == null) {
            return;
        }

        if (abilityItemFactory.isAbilityItem(item, beyonder)) {
            event.getItemDrop().remove();
        }
    }

    /**
     * Видалення предметів меню/здібностей з дропа після смерті
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Beyonder beyonder = beyonderService.getBeyonder(event.getEntity().getUniqueId());
        if (beyonder == null) {
            return;
        }

        event.getDrops().removeIf(item ->
                abilityMenu.isAbilityMenu(item) ||
                        abilityItemFactory.isAbilityItem(item, beyonder));
    }

    /**
     * Повернення предмета меню після респавну
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            return;
        }

        // Даємо предмет меню назад через тік
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("mysteries-above"),
                () -> abilityMenu.giveAbilityMenuItemToPlayer(player, beyonder),
                1L
        );
    }
}