package me.vangoo.domain.pathways.visionary;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class VisionaryPotions extends PathwayPotions {
    public VisionaryPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.GRAY, List.of(), itemResolver);
        addIngredientsRecipe(9, new ItemStack(Material.BONE), new ItemStack(Material.COBBLESTONE));
    }
}
