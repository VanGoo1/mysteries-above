package me.vangoo.domain.pathways.error;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ErrorPotions extends PathwayPotions {
    public ErrorPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.RED, List.of(), itemResolver);

        var blackMosquito = itemResolver.createItemStack("black_mosquito");
        var lavarOctopusCrystal = itemResolver.createItemStack("lavar_octopus_crystal");
        var sphinxBrain = itemResolver.createItemStack("sphinx_brain");
        var plagueSerpentBile = itemResolver.createItemStack("plague_serpent_bile");

        if (blackMosquito.isPresent() && lavarOctopusCrystal.isPresent() &&
                sphinxBrain.isPresent() && plagueSerpentBile.isPresent()) {

            addIngredientsRecipe(9, blackMosquito.get(), sphinxBrain.get());
            addIngredientsRecipe(8, sphinxBrain.get(), lavarOctopusCrystal.get(), blackMosquito.get());
            addIngredientsRecipe(7, plagueSerpentBile.get(), blackMosquito.get());
        }
    }
}