package me.vangoo.pathways.whitetower;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

public class WhiteTowerPotions extends PathwayPotions {
    public WhiteTowerPotions(Pathway pathway, IItemResolver itemResolver,
                             Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
