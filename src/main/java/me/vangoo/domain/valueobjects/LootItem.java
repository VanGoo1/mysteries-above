package me.vangoo.domain.valueobjects;

public record LootItem(
        String itemId,
        int weight,
        int minAmount,
        int maxAmount
) {}