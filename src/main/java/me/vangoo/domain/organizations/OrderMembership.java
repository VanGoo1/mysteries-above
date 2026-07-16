package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Членство в таємній організації. Очок НЕМАЄ: ранг виводиться з послідовності
 * ({@link OrderRank}), нагороди — персистентні фавори. Куратор — наративна персона,
 * генерується при вступі й живе з членством.
 */
public class OrderMembership {

    private final UUID playerId;
    private final String institutionId;
    private final String curatorName;
    private List<OrderTask> tasks = new ArrayList<>();
    private long lastTaskRefreshEpochMillis;
    private int taskSetsUsed;
    private final List<Favor> favors = new ArrayList<>();

    public OrderMembership(UUID playerId, String institutionId, String curatorName) {
        this.playerId = playerId;
        this.institutionId = institutionId;
        this.curatorName = curatorName;
    }

    public UUID playerId() { return playerId; }

    public String institutionId() { return institutionId; }

    public String curatorName() { return curatorName; }

    public List<OrderTask> tasks() { return tasks; }

    public void setTasks(List<OrderTask> tasks) {
        this.tasks = new ArrayList<>(tasks);
    }

    public long lastTaskRefreshEpochMillis() { return lastTaskRefreshEpochMillis; }

    public void setLastTaskRefreshEpochMillis(long millis) {
        this.lastTaskRefreshEpochMillis = millis;
    }

    public int taskSetsUsed() { return taskSetsUsed; }

    public void consumeTaskSet() { taskSetsUsed++; }

    public void startTaskWindow(long now) {
        this.lastTaskRefreshEpochMillis = now;
        this.taskSetsUsed = 0;
    }

    public void restoreTaskSetsUsed(int used) {
        this.taskSetsUsed = Math.max(0, used);
    }

    public List<Favor> favors() {
        return Collections.unmodifiableList(favors);
    }

    public void addFavor(Favor favor) {
        favors.add(favor);
    }

    /** Списує НАЙДЕШЕВШИЙ фавор, що покриває вагу — MAJOR не палиться на дрібне прохання. */
    public Optional<Favor> consumeFavor(TaskWeight required) {
        Optional<Favor> cheapest = favors.stream()
                .filter(f -> f.weight().atLeast(required))
                .min(Comparator.comparing(f -> f.weight().ordinal()));
        cheapest.ifPresent(favors::remove);
        return cheapest;
    }

    /** Гідрація з persisted-стану. */
    public void restoreFavors(List<Favor> saved) {
        favors.clear();
        if (saved != null) {
            favors.addAll(saved);
        }
    }
}
