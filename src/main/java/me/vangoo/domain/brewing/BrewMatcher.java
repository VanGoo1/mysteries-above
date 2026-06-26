package me.vangoo.domain.brewing;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Підбирає перший рецепт із набору, що збігається з наданими інгредієнтами. */
public final class BrewMatcher {

    public Optional<BrewRecipe> findMatch(Collection<BrewRecipe> recipes, Map<String, Integer> provided) {
        for (BrewRecipe recipe : recipes) {
            if (recipe.matches(provided)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }
}
