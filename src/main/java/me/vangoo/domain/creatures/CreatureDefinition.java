package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;

/**
 * Правила спавну/луту істоти. Контент моба (стати, вигляд, скіли) живе у MythicMobs-паку;
 * {@code id} — internal name MythicMobs-моба (join-ключ), {@code baseEntityType} — базовий
 * ванільний тип (дублює Type у паку; потрібен чистому домену для aquatic-логіки ambient-спавну).
 */
public record CreatureDefinition(
        String id,
        String baseEntityType,
        CreatureTier tier,
        LootTableData loot,
        SpawnRule spawn,
        String pathway,
        int sequence) {}
