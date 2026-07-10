package me.vangoo.domain.creatures;

import java.util.UUID;

/**
 * Кандидат-магніт Закону Конвергенції: онлайн-Beyonder, до якого може тяжіти резонансне джерело.
 *
 * @param id            UUID гравця
 * @param pathway       назва шляху (порівняння без регістру)
 * @param group         назва PathwayGroup (для резонансу «сусід»)
 * @param sequenceLevel поточний рівень послідовності (9 = найслабший, 0 = найсильніший)
 * @param x,z           горизонтальні координати
 */
public record ResonantBeyonder(UUID id, String pathway, String group, int sequenceLevel,
                               double x, double z) {}
