package me.vangoo.domain.creatures;

import java.util.List;

public record SpawnRule(
        List<String> naturalBiomes,
        List<String> naturalReplaces,
        double naturalChance,
        List<String> structureKeys,
        double structureChance) {}
