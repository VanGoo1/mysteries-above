package me.vangoo.implementation.ErrorPathway;

import me.vangoo.domain.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ErrorPotions extends PathwayPotions {
    public ErrorPotions(Pathway pathway, Color potionColor) {
        super(pathway, potionColor, ChatColor.RED, List.of("bla bla bla...."));
        addMainSupplementaryRecipe(9, new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.ANVIL));
    }
}
