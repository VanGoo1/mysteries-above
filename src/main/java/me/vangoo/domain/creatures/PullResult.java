package me.vangoo.domain.creatures;

import java.util.UUID;

/**
 * Результат правила тяжіння: до кого тяжіє джерело і наскільки сильно.
 *
 * @param targetId UUID обраного магніта
 * @param strength сила нуджа ∈ (0,1]
 */
public record PullResult(UUID targetId, double strength) {}
