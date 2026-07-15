package me.vangoo.domain.organizations;

/** Завдання церкви: полювання (targetKey = id істоти) або доставка (targetKey = itemKey). */
public record ChurchTask(Type type, String targetKey, String targetName,
                         int required, int progress, int rewardPoints) {

    public enum Type { HUNT, DELIVER }

    public static ChurchTask hunt(String creatureId, String displayName, int sequence) {
        int required = 1 + sequence / 3;
        return new ChurchTask(Type.HUNT, creatureId, displayName, required, 0,
                (10 - sequence) * 12 * required);
    }

    public static ChurchTask deliver(String itemKey, String displayName, int sequence) {
        int required = 2 + sequence / 2;
        return new ChurchTask(Type.DELIVER, itemKey, displayName, required, 0,
                (10 - sequence) * 4 * required);
    }

    public ChurchTask withProgress(int newProgress) {
        return new ChurchTask(type, targetKey, targetName, required,
                Math.min(newProgress, required), rewardPoints);
    }

    public boolean isComplete() {
        return progress >= required;
    }
}
