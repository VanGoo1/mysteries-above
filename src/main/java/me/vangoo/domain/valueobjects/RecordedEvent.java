package me.vangoo.domain.valueobjects;

import org.bukkit.Location;

public class RecordedEvent {
    private final Location location;
    private final String description;
    private final long timestamp;
    private final EventType type;

    public enum EventType {
        BLOCK_BREAK,
        BLOCK_PLACE,
        CONTAINER_OPEN,
        CONTAINER_TRANSACTION,
        DEATH
    }

    public RecordedEvent(Location location, String description, EventType type, long timestamp) {
        this.location = location;
        this.description = description;
        this.type = type;
        this.timestamp = timestamp;
    }

    public Location getLocation() { return location; }
    public String getDescription() { return description; }
    public long getTimestamp() { return timestamp; }
    public EventType getType() { return type; }
}