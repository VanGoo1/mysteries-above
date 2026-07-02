package me.vangoo.domain.valueobjects;

public record LootItem(
        String itemId,
        int weight,
        int minAmount,
        int maxAmount,
        LootTier tier
) {
    /** Зворотно-сумісний конструктор: без указаного тіру предмет = BASE. */
    public LootItem(String itemId, int weight, int minAmount, int maxAmount) {
        this(itemId, weight, minAmount, maxAmount, LootTier.BASE);
    }
}
