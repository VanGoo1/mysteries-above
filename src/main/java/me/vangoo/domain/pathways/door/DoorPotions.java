package me.vangoo.domain.pathways.door;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class DoorPotions extends PathwayPotions {
    public DoorPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.RED, List.of(), itemResolver);
        addIngredientsRecipe(9, new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.ANVIL));
    }
}
