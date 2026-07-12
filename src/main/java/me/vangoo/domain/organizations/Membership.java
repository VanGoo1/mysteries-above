package me.vangoo.domain.organizations;

import java.util.UUID;

/**
 * Членство гравця в церкві: lifetime-вклад (визначає ранг, не зменшується)
 * і баланс очок (валюта замовлень зілля).
 */
public class Membership {

    private final UUID playerId;
    private final String institutionId;
    private int lifetimeContribution;
    private int balance;

    public Membership(UUID playerId, String institutionId) {
        this.playerId = playerId;
        this.institutionId = institutionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String institutionId() {
        return institutionId;
    }

    public int lifetimeContribution() {
        return lifetimeContribution;
    }

    public int balance() {
        return balance;
    }

    public void addContribution(int points) {
        requirePositive(points);
        lifetimeContribution += points;
        balance += points;
    }

    /** @return false, якщо балансу бракує (нічого не змінюється). */
    public boolean spend(int points) {
        requirePositive(points);
        if (balance < points) {
            return false;
        }
        balance -= points;
        return true;
    }

    public ChurchRank rank(int[] thresholds) {
        return ChurchRank.of(lifetimeContribution, thresholds);
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
    }
}
