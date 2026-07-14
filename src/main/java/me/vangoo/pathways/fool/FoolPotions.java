package me.vangoo.pathways.fool;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

/**
 * Potion recipes for the Fool pathway. Інгредієнти беруться з potion-recipes.yml.
 */
public class FoolPotions extends PathwayPotions {
    public FoolPotions(Pathway pathway, IItemResolver itemResolver,
                       Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
