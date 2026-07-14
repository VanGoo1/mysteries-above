package me.vangoo.pathways.justiciar;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

public class JusticiarPotions extends PathwayPotions {
    public JusticiarPotions(Pathway pathway, IItemResolver itemResolver,
                            Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
