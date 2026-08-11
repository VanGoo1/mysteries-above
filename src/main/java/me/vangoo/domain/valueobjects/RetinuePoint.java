package me.vangoo.domain.valueobjects;

/**
 * Позиція в світі для персистентного почту нежиті (Death, Посл. 6) — світ і координати
 * примітивами, як {@link Waypoint}; конвертацію в {@code Location} робить ефект-шар.
 */
public record RetinuePoint(String world, double x, double y, double z, float yaw) {
}
