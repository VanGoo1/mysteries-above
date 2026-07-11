package me.vangoo.infrastructure.market;

/**
 * Чиста геометрія зали зборів: де стоїть Посередник і де розставити учасників,
 * щоб вони не спавнились один в одному. Координати відносні до центру підлоги
 * (0.5, 0.5); GatheringVenueProvider перетворює їх на Location зі світом і y.
 */
public final class VenueLayout {

    /** Позиція+поворот (x,z у блоках, yaw у градусах Minecraft). */
    public record Spot(double x, double z, float yaw) {}

    private static final double ORG_X = 0.5;
    private static final double ORG_Z = -6.5; // північний край зали
    private static final double SPACING = 2.0;

    private VenueLayout() {}

    /** Посередник — на чолі зали, обличчям до учасників (+z). */
    public static Spot organizer() {
        return new Spot(ORG_X, ORG_Z, 0f);
    }

    /** i-й з total учасників: сітка перед Посередником, обличчям до нього. */
    public static Spot attendee(int index, int total) {
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(total)));
        int row = index / cols;
        int col = index % cols;
        double x = ORG_X + (col - (cols - 1) / 2.0) * SPACING;
        double z = 1.5 + row * SPACING; // починається за кілька блоків від Посередника, тягнеться до +z
        x = clamp(x);
        z = clamp(z);
        return new Spot(x, z, yawToward(x, z, ORG_X, ORG_Z));
    }

    private static double clamp(double v) {
        return Math.max(-7.5, Math.min(7.5, v));
    }

    /** Yaw, що дивиться з (x,z) на (tx,tz) за конвенцією Minecraft. */
    private static float yawToward(double x, double z, double tx, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // Normalize to [0, 360) to ensure test assertions work correctly
        if (angle < 0) {
            angle += 360f;
        }
        return angle;
    }
}
