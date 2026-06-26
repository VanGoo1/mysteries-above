package me.vangoo.domain.brewing;

import java.util.List;

/** Сирий опис рецепта з конфіга: id основних та допоміжних інгредієнтів (без Bukkit). */
public record RecipeDefinition(List<String> mainIds, List<String> auxIds) {
    public RecipeDefinition {
        // Незмінний VO: ізолюємось від мутації переданих списків.
        mainIds = List.copyOf(mainIds);
        auxIds = List.copyOf(auxIds);
    }
}
