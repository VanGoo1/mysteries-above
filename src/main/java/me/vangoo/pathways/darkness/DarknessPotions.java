package me.vangoo.pathways.darkness;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

/** Зілля шляху Darkness — заготовка: кольори з брендингу, рецептів варіння ще немає. */
public class DarknessPotions extends PathwayPotions {
    public DarknessPotions(Pathway pathway, IItemResolver itemResolver,
                         Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
