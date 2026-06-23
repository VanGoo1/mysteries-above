package me.vangoo.domain.spells;

/**
 * Те, що гравець "накликав" у GUI-конструкторі заклинань: вибір типу + рівні покращень (0–5).
 * Чистий запис без Bukkit — GUI лише збирає його, а доменна математика
 * ({@link SpellRecipe#fromBlueprint}) перетворює його на готовий рецепт.
 */
public record SpellBlueprint(
        String name,
        SpellRecipe.Shape shape,
        int powerLvl,          // Шкода (PROJECTILE/AOE)
        int areaLvl,           // Радіус / Дальність / Тривалість бафа
        int healLvl,           // Зцілення (SELF) / Рівень ефекту (BUFF)
        int cooldownLvl,       // Зменшення кулдауну
        int costReductionLvl,  // Оптимізація витрат духовності
        SpellRecipe.Buff buff  // лише для shape == BUFF
) {
}
