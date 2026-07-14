package me.vangoo.pathways.hermit;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

/** Зілля шляху Hermit — заготовка: кольори з брендингу, рецептів варіння ще немає. */
public class HermitPotions extends PathwayPotions {
    public HermitPotions(Pathway pathway, IItemResolver itemResolver,
                         Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
