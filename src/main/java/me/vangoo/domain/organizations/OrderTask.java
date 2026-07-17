package me.vangoo.domain.organizations;

/**
 * Завдання ордену. targetKey: DELIVER → itemKey, HUNT → creatureId,
 * RAID/ASSASSINATE/RECON/SABOTAGE → institutionId церкви-цілі.
 */
public record OrderTask(Type type, TaskWeight weight, String targetKey, String targetName,
                        int required, int progress) {

    public enum Type { DELIVER, HUNT, RAID, ASSASSINATE, RECON, SABOTAGE }

    public static OrderTask deliver(String itemKey, String displayName, int sequence) {
        return new OrderTask(Type.DELIVER, weightForSequence(sequence), itemKey, displayName,
                2 + sequence / 2, 0);
    }

    public static OrderTask hunt(String creatureId, String displayName, int sequence) {
        return new OrderTask(Type.HUNT, weightForSequence(sequence), creatureId, displayName,
                1 + sequence / 3, 0);
    }

    public static OrderTask raid(String churchId, String churchName) {
        return new OrderTask(Type.RAID, TaskWeight.MAJOR, churchId, churchName, 1, 0);
    }

    public static OrderTask assassinate(String churchId, String churchName) {
        return new OrderTask(Type.ASSASSINATE, TaskWeight.MAJOR, churchId, churchName, 1, 0);
    }

    public static OrderTask recon(String churchId, String churchName) {
        return new OrderTask(Type.RECON, TaskWeight.STANDARD, churchId, churchName, 1, 0);
    }

    public static OrderTask sabotage(String churchId, String churchName) {
        return new OrderTask(Type.SABOTAGE, TaskWeight.MAJOR, churchId, churchName, 1, 0);
    }

    /** Слабкі послідовності (9..6) — легка розминка; сильніші цілі — STANDARD. */
    private static TaskWeight weightForSequence(int sequence) {
        return sequence >= 6 ? TaskWeight.LIGHT : TaskWeight.STANDARD;
    }

    public OrderTask withProgress(int newProgress) {
        return new OrderTask(type, weight, targetKey, targetName, required,
                Math.min(newProgress, required));
    }

    public boolean isComplete() {
        return progress >= required;
    }

    public boolean isTempleOp() {
        return type == Type.RAID || type == Type.ASSASSINATE;
    }

    public boolean isSpyOp() {
        return type == Type.RECON || type == Type.SABOTAGE;
    }
}
