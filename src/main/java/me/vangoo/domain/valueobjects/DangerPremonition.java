package me.vangoo.domain.valueobjects;

/**
 * Балансні числа покращеної Інтуїції небезпеки (Seq 9 Провидця шляху Блазня).
 *
 * <p>Усі значення зростають зі силою Beyonder'а (нижча послідовність = потужніше
 * передчуття). Чиста математика — без Bukkit, тестується юнітами. Ефекти
 * (партикли, glow, ривок) живуть у {@code pathways.fool.abilities.DangerIntuition}.
 */
public final class DangerPremonition {

    private DangerPremonition() {
    }

    /** 0 (Seq 9) … 9 (Seq 0). */
    private static int power(Sequence sequence) {
        return 9 - sequence.level();
    }

    /**
     * Шанс автоматично ухилитися від удару, що був би смертельним.
     * Seq 9 ≈ 12%, зростає до стелі 40% (ніколи не абсолютний щит).
     */
    public static double lethalDodgeChance(Sequence sequence) {
        double chance = 0.12 + power(sequence) * 0.03;
        return clamp(chance, 0.0, 0.40);
    }

    /**
     * Внутрішній кулдаун між авто-ухиленнями (мс). Сильніший Beyonder
     * відновлює передчуття швидше: Seq 9 = 20с … не коротше 6с.
     */
    public static long lethalDodgeCooldownMillis(Sequence sequence) {
        long ms = 20_000L - power(sequence) * 1_500L;
        return Math.max(6_000L, ms);
    }

    /**
     * Шанс (за одну перевірку) показати натяк на наступну дію ворога,
     * за яким спостерігає Beyonder. Рідкісний прояв: Seq 9 ≈ 4% … стеля 25%.
     */
    public static double actionPredictionChance(Sequence sequence) {
        double chance = 0.04 + power(sequence) * 0.015;
        return clamp(chance, 0.0, 0.25);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
