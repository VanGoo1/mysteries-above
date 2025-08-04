package me.vangoo.implementation.ErrorPathway.abilities;

import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class KillYourselfAbility implements Ability {
    @Override
    public String getName() {
        return "kys";
    }

    @Override
    public String getDescription() {
        return "ability that kill yourself";
    }

    @Override
    public int getSpiritualityCost() {
        return 20;
    }

    @Override
    public void execute(Player caster, Beyonder beyonder) {
        caster.setHealth(0);
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = new ItemStack(Material.IRON_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + getName());
        meta.setLore(List.of(getDescription(), "Cost: " + getSpiritualityCost()));
        item.setItemMeta(meta);
        return item;
    }
}
