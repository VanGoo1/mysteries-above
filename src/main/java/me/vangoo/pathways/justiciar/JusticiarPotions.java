package me.vangoo.pathways.justiciar;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class JusticiarPotions extends PathwayPotions {
    public JusticiarPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                            Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.GOLD, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
