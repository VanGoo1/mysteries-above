package me.vangoo.domain.valueobjects;

/** Тимчасовий множник шкоди (Sun {@code Notarization}); {@code isActive} перевіряє момент часу без окремого таску. */
public record AmplificationBuff(double multiplier, long expiresAtMillis) {

    public boolean isActive(long nowMillis) {
        return nowMillis < expiresAtMillis;
    }
}
