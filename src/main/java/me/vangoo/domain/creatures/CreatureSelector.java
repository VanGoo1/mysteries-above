package me.vangoo.domain.creatures;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Чисте правило вибору істоти для спавну (аналог BrewMatcher). Детерміноване при поданому roll.
 * Кожен кандидат займає сегмент [cumulative, cumulative+chance) на осі [0,1); roll за сумою
 * всіх шансів означає «спавну немає».
 */
public final class CreatureSelector {

    private final List<CreatureDefinition> creatures;

    public CreatureSelector(Collection<CreatureDefinition> creatures) {
        this.creatures = List.copyOf(creatures);
    }

    public Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll) {
        double cumulative = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.naturalChance() <= 0.0) continue;
            if (!s.naturalBiomes().contains(biome)) continue;
            if (!s.naturalReplaces().contains(baseEntityType)) continue;
            cumulative += s.naturalChance();
            if (roll < cumulative) return Optional.of(def);
        }
        return Optional.empty();
    }

    public Optional<CreatureDefinition> pickForStructure(String structureKey, double roll) {
        double cumulative = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.structureChance() <= 0.0) continue;
            if (s.structureKeys().stream().noneMatch(structureKey::contains)) continue;
            cumulative += s.structureChance();
            if (roll < cumulative) return Optional.of(def);
        }
        return Optional.empty();
    }
}
