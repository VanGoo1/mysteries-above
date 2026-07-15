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
    private PotionOrder activeOrder;        // null = нема замовлення
    // Ключі "<шлях>:<послідовність>" усіх колись замовлених зілль. Живе на членстві,
    // тож вихід із церкви стирає історію разом із самим Membership.
    private final java.util.Set<String> orderedPotionKeys = new java.util.HashSet<>();

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

    public PotionOrder activeOrder() { return activeOrder; }

    public void setActiveOrder(PotionOrder order) { this.activeOrder = order; }

    public void clearActiveOrder() { this.activeOrder = null; }

    /** Ключ зілля в історії замовлень. */
    public static String orderKey(String pathwayName, int sequence) {
        return pathwayName + ":" + sequence;
    }

    public boolean hasOrdered(String pathwayName, int sequence) {
        return orderedPotionKeys.contains(orderKey(pathwayName, sequence));
    }

    public void markOrdered(String pathwayName, int sequence) {
        orderedPotionKeys.add(orderKey(pathwayName, sequence));
    }

    public java.util.Set<String> orderedPotionKeys() {
        return java.util.Collections.unmodifiableSet(orderedPotionKeys);
    }

    /** Гідрація з persisted-стану; null (старий memberships.json) = порожня історія. */
    public void restoreOrderedPotionKeys(java.util.Collection<String> keys) {
        orderedPotionKeys.clear();
        if (keys != null) {
            orderedPotionKeys.addAll(keys);
        }
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
    }
}
