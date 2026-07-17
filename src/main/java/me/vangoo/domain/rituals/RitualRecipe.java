package me.vangoo.domain.rituals;

import java.util.Map;

/**
 * Чистий рецепт ритуалу: вимоги до вівтаря й інгредієнтів + гейт послідовності.
 * Інгредієнти — імена Bukkit Material як String (домен не імпортує Bukkit);
 * резолвить їх шар поведінки.
 */
public record RitualRecipe(
        RitualType type,
        String displayName,
        String description,
        int minSequence,
        int candlesRequired,
        Map<String, Integer> ingredients,
        boolean requiresHandSacrifice
) {
    public boolean availableAt(int sequenceLevel) {
        return sequenceLevel <= minSequence;
    }
}
