package me.vangoo.pathways.abyss;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

/** Зілля шляху Abyss — заготовка: кольори з брендингу, рецептів варіння ще немає. */
public class AbyssPotions extends PathwayPotions {
    public AbyssPotions(Pathway pathway, IItemResolver itemResolver,
                         Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
