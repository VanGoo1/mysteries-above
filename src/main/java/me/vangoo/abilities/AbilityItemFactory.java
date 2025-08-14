package me.vangoo.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AbilityItemFactory {

    public ItemStack createItem(Ability ability, Map<String, String> additionalStatsLore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ability.getName());
            List<String> lore = new ArrayList<>(Arrays.asList(
                    ChatColor.GRAY + ability.getDescription(),
                    ChatColor.GRAY + "--------------------------------------------",
                    ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + ability.getSpiritualityCost()
            ));

            if (!ability.isPassive()) {
                lore.add(ChatColor.GRAY + "Кулдаун: " + ChatColor.BLUE + ability.getCooldown() + "c");
            }
            additionalStatsLore.forEach((key, value) -> {
                lore.add(ChatColor.GRAY + key + ": " + ChatColor.BLUE + value);
            });

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
