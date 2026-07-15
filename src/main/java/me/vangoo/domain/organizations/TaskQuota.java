package me.vangoo.domain.organizations;

/**
 * Правило поповнення пулу завдань: добове вікно + квота наборів на вікно.
 *
 * <p>Набір видається ЦІЛИМ і лише коли пул порожній («закрий набір — отримаєш новий»),
 * тож квота в наборах лишається чесною одиницею: {@code setsPerDay} × розмір набору
 * = стеля завдань на вікно. Саме вона обмежує швидкість фарму очок вкладу, які
 * визначають ранг і оплачують замовлення зілль.
 *
 * <p>Стан (початок вікна + витрачені набори) живе на {@link Membership} — тут лише
 * чиста математика над ним.
 */
public record TaskQuota(long windowMillis, int setsPerDay) {

    public TaskQuota {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive: " + windowMillis);
        }
        if (setsPerDay <= 0) {
            throw new IllegalArgumentException("setsPerDay must be positive: " + setsPerDay);
        }
    }

    /** Вікно скінчилось (або початок у майбутньому — переведений годинник). */
    public boolean windowExpired(long windowStartMillis, long now) {
        long elapsed = now - windowStartMillis;
        return elapsed < 0 || elapsed >= windowMillis;
    }

    public boolean canGenerate(int setsUsed) {
        return setsUsed < setsPerDay;
    }

    public int setsLeft(int setsUsed) {
        return Math.max(0, setsPerDay - setsUsed);
    }

    /** Скільки лишилось до скидання квоти; 0 — вікно вже можна скидати. */
    public long millisUntilReset(long windowStartMillis, long now) {
        if (windowExpired(windowStartMillis, now)) {
            return 0L;
        }
        return windowStartMillis + windowMillis - now;
    }
}
