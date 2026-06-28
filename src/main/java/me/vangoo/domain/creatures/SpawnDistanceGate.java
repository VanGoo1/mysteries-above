package me.vangoo.domain.creatures;

/** Чисте правило: чи достатньо далеко (по горизонталі) точка від спавну світу для появи істот. */
public final class SpawnDistanceGate {

    private SpawnDistanceGate() {}

    /**
     * @param dx          різниця X від точки спавну світу
     * @param dz          різниця Z від точки спавну світу
     * @param minDistance мінімальна горизонтальна відстань у блоках
     * @return true, якщо sqrt(dx^2 + dz^2) >= minDistance
     */
    public static boolean isFarEnough(double dx, double dz, double minDistance) {
        if (minDistance <= 0.0) return true;
        return (dx * dx + dz * dz) >= (minDistance * minDistance);
    }
}
