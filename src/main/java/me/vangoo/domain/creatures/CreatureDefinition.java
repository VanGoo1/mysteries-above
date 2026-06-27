package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;

import java.util.Map;

public record CreatureDefinition(
        String id,
        String baseEntityType,
        String displayName,
        CreatureTier tier,
        CreatureStats stats,
        Map<String, String> equipment,
        String appearance,
        LootTableData loot,
        SpawnRule spawn,
        boolean clearVanillaDrops) {}
