package me.vangoo.pathways.common;

import org.bukkit.entity.Entity;

/**
 * Захист душі — ванільний scoreboard-тег, який ставить одна підсистема, а читають кілька.
 *
 * <p>Перший носій — Обмін духом Провідника духів (Death, Посл. 6): провідник міняється місцями
 * зі слугою, і 30 с усе, що б'є по душі, б'є по нежиті замість нього. Перевіряють захист чужі
 * шляхи (Нитки Маріонетиста з Fool, Мова мертвих із самої Смерті), тож жити він мусить там, де
 * його видно всім, — у {@code pathways.common}, за зразком {@link Spirits}.
 *
 * <p>Тег, а не мапа в сервісі, саме тому, що читачі в різних шляхах: інстанс реєстру одного
 * шляху з іншого не дістати, а {@code static}-стан на здібності заборонений. Рестарт тег не
 * переживає — і не мусить: 30 с, що переживають рестарт, це вже не щит, а безсмертя.
 */
public final class SoulWard {

    /** Літерал тега живе лише тут: розбіжність компілятор не спіймав би. */
    public static final String TAG = "ma_soul_ward";

    private SoulWard() {
    }

    /** Чи ця істота захищена від душевних/ментальних впливів. */
    public static boolean isWarded(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    public static void protect(Entity entity) {
        if (entity != null) entity.addScoreboardTag(TAG);
    }

    public static void drop(Entity entity) {
        if (entity != null) entity.removeScoreboardTag(TAG);
    }
}
