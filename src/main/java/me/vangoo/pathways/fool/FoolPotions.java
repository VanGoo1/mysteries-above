package me.vangoo.pathways.fool;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

/**
 * Potion recipes for the Fool pathway. Інгредієнти беруться з potion-recipes.yml.
 */
public class FoolPotions extends PathwayPotions {
    public FoolPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                       Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.DARK_PURPLE, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
