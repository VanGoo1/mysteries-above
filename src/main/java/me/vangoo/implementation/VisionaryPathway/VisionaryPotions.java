package me.vangoo.implementation.VisionaryPathway;

import me.vangoo.pathways.Pathway;
import me.vangoo.potions.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class VisionaryPotions extends PathwayPotions {
    public VisionaryPotions(Pathway pathway, Color potionColor) {
        super(pathway, potionColor, ChatColor.GRAY, List.of("Шлях: "+pathway.getName()));
        addMainSupplementaryRecipe(9, new ItemStack(Material.BONE), new ItemStack(Material.COBBLESTONE));
    }
}
