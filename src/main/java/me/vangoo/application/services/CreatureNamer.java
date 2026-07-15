package me.vangoo.application.services;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Укр-назва істоти та її біоми для UI (дзеркалить {@link MarketItemNamer} для предметів).
 *
 * <p>Назва береться з MythicMobs-пака ({@code Display:}) — єдиного джерела правди для
 * вигляду моба; creatures.yml її свідомо не дублює. Біоми — навпаки, з creatures.yml,
 * бо правила спавну живуть у коді, а не в паку.
 */
public class CreatureNamer {

    /** Біоми, задіяні в creatures.yml. Невідомий ключ → humanize (не падаємо). */
    private static final Map<String, String> BIOME_NAMES = new HashMap<>();
    static {
        BIOME_NAMES.put("BADLANDS", "Пустище");
        BIOME_NAMES.put("DARK_FOREST", "Темний ліс");
        BIOME_NAMES.put("DEEP_OCEAN", "Глибокий океан");
        BIOME_NAMES.put("DESERT", "Пустеля");
        BIOME_NAMES.put("FOREST", "Ліс");
        BIOME_NAMES.put("JUNGLE", "Джунглі");
        BIOME_NAMES.put("MANGROVE_SWAMP", "Мангрові болота");
        BIOME_NAMES.put("OCEAN", "Океан");
        BIOME_NAMES.put("PLAINS", "Рівнини");
        BIOME_NAMES.put("RIVER", "Річка");
        BIOME_NAMES.put("SAVANNA", "Савана");
        BIOME_NAMES.put("SWAMP", "Болото");
        BIOME_NAMES.put("TAIGA", "Тайга");
        BIOME_NAMES.put("WARM_OCEAN", "Теплий океан");
    }

    private final MythicCreatureGateway gateway;
    private final Map<String, CreatureDefinition> creatureRegistry;

    public CreatureNamer(MythicCreatureGateway gateway,
                         Map<String, CreatureDefinition> creatureRegistry) {
        this.gateway = gateway;
        this.creatureRegistry = creatureRegistry;
    }

    /** Укр-назва з пака; фолбек — читабельний id (моба ще не додали в пак). */
    public String displayName(String creatureId) {
        return gateway.displayNameOf(creatureId).orElseGet(() -> humanize(creatureId));
    }

    /** Укр-назви біомів природного спавну; порожньо — істота в дикій природі не з'являється. */
    public List<String> biomeNames(String creatureId) {
        CreatureDefinition definition = creatureRegistry.get(creatureId);
        if (definition == null || definition.spawn() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String biome : definition.spawn().naturalBiomes()) {
            names.add(biomeName(biome));
        }
        return names;
    }

    /** Чи трапляється істота біля структур (apex-тір) — окрема підказка для гравця. */
    public boolean spawnsNearStructures(String creatureId) {
        CreatureDefinition definition = creatureRegistry.get(creatureId);
        return definition != null && definition.spawn() != null
                && definition.spawn().structureChance() > 0.0;
    }

    static String biomeName(String rawBiome) {
        if (rawBiome == null || rawBiome.isBlank()) {
            return "";
        }
        String key = rawBiome.toUpperCase(Locale.ROOT);
        String known = BIOME_NAMES.get(key);
        return known != null ? known : humanize(key);
    }

    private static String humanize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String spaced = raw.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        if (spaced.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
