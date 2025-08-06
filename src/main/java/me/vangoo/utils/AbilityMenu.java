package me.vangoo.utils;

import me.vangoo.LotmPlugin;
import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import me.vangoo.beyonders.BeyonderManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.Bukkit.createInventory;

public class AbilityMenu {
    private static final String MENU_TITLE = "§8Меню Здібностей";
    private static final int MENU_SIZE = 54;


    public AbilityMenu() {

    }

    private ItemStack getItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Містичні здібності");
        meta.addEnchant(Enchantment.AQUA_AFFINITY, 2, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Меню здібностей потойбічного");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    public void openMenu(Player player, Beyonder beyonder) {
        if (beyonder == null) {
            return;
        }
        Inventory menu = createMenu(beyonder.getAbilities());
        player.openInventory(menu);
    }

    private Inventory createMenu(List<Ability> availableAbilities) {
        Inventory menu = createInventory(null, MENU_SIZE, MENU_TITLE);

        for (int i = 0; i < availableAbilities.size(); i++) {
            ItemStack item = availableAbilities.get(i).getItem();
            NBTBuilder nbtBuilder = new NBTBuilder(item);
            item = nbtBuilder.setBoolean("isInAbilityMenu", true).build();
            menu.setItem(i, item);
        }

        return menu;
    }

    public boolean isAbilityMenu(ItemStack item) {
        return getItem().isSimilar(item);
    }

    public boolean isAbilityItemInMenu(ItemStack item) {
        return NBTBuilder.hasKey(item, "isInAbilityMenu", PersistentDataType.BOOLEAN);
    }

    public void giveAbilityMenuItemToPlayer(Player player) {
        if (player.getInventory().contains(getItem())) {
            return;
        }
        player.getInventory().setItem(9, getItem());
    }
}
