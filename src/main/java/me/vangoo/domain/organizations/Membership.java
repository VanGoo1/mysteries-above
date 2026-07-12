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
    private java.util.List<ChurchTask> tasks = new java.util.ArrayList<>();
    private long lastTaskRefreshEpochMillis;
    private ChurchTask initiationTask;      // null = не активна
    private String initiationPathway;       // шлях зілля, обраний при ініціації

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

    public java.util.List<ChurchTask> tasks() { return tasks; }

    public void setTasks(java.util.List<ChurchTask> tasks) {
        this.tasks = new java.util.ArrayList<>(tasks);
    }

    public long lastTaskRefreshEpochMillis() { return lastTaskRefreshEpochMillis; }

    public void setLastTaskRefreshEpochMillis(long millis) {
        this.lastTaskRefreshEpochMillis = millis;
    }

    public ChurchTask initiationTask() { return initiationTask; }

    public String initiationPathway() { return initiationPathway; }

    public void setInitiation(ChurchTask task, String pathwayName) {
        this.initiationTask = task;
        this.initiationPathway = pathwayName;
    }

    public void clearInitiation() {
        this.initiationTask = null;
        this.initiationPathway = null;
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
    }
}
