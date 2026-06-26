package me.vangoo.pathways.error;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class ErrorPotions extends PathwayPotions {
    public ErrorPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                        Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.RED, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
