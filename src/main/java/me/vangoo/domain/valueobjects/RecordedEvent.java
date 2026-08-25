package me.vangoo.domain.valueobjects;

/**
 * Слід минулої події з CoreProtect. Чистий VO — світ і координати зберігаємо
 * примітивами, як {@link Waypoint} і {@link RetinuePoint}; конвертацію в
 * {@code Location} робить ефект-шар ({@code pathways.common.RecordedEvents}).
 */
public class RecordedEvent {
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final String description;
    private final long timestamp;
    private final EventType type;
    /** Сире ім'я винуватця з CoreProtect (може бути null або службове "#tnt"). */
    private final String actor;

    public enum EventType {
        BLOCK_BREAK,
        BLOCK_PLACE,
        CONTAINER_OPEN,
        CONTAINER_TRANSACTION,
        DEATH
    }

    public RecordedEvent(String world, double x, double y, double z,
                         String description, EventType type, long timestamp, String actor) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.description = description;
        this.type = type;
        this.timestamp = timestamp;
        this.actor = actor;
    }

    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getDescription() { return description; }
    public long getTimestamp() { return timestamp; }
    public EventType getType() { return type; }

    /** Ім'я винуватця без форматування; null, якщо CoreProtect його не дав. */
    public String getActor() { return actor; }
}
