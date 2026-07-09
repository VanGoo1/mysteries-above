package me.vangoo.infrastructure.forage;

import me.vangoo.domain.forage.ForageDonorMap;
import me.vangoo.domain.forage.ForageEntry;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Незмінний знімок forage.yml: параметри спавну, цілі (флора/листя), донори, таблиці біомів. */
public record ForageConfig(
        long intervalSeconds,
        double chance,
        int maxNearby,
        long ttlSeconds,
        int searchRadius,
        long particlePeriodTicks,
        Set<Material> vegetation,
        Set<Material> leaves,
        ForageDonorMap donors,
        Map<String, List<ForageEntry>> biomes
) {}
