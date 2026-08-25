package me.vangoo.domain.rituals;

/**
 * Рядок Книги одкровення: як інгредієнт зветься і звідки береться.
 * {@code source} — null, якщо джерело невідоме (інгредієнт лише зі скринь).
 */
public record IngredientHint(String displayName, IngredientSourceIndex.Source source) {
}
