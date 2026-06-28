package me.vangoo.domain.creatures;

/**
 * Закон конвергенції: схиляє ВИБІР істоти на природному спавні до шляху гравця та послідовності,
 * яка йому скоро знадобиться. Не змінює загальну ймовірність спавну — лише перерозподіляє її.
 *
 * @param pathway       назва шляху гравця (порівняння без урахування регістру з CreatureDefinition.pathway)
 * @param sequenceLevel поточний рівень послідовності гравця (9 = найслабший, 0 = найсильніший)
 */
public record ConvergenceBias(String pathway, int sequenceLevel) {}
