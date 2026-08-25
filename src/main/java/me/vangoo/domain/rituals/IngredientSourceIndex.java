package me.vangoo.domain.rituals;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.forage.ForageEntry;
import me.vangoo.domain.valueobjects.LootItem;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Звідки береться інгредієнт: із луту істоти ({@code creatures.yml}) чи з фореджу
 * ({@code forage.yml}). Джерело Ритуалу одкровення (RitualType.BESTOWMENT).
 * Чистий домен: біоми лишаються сирими ключами, а текст натяку складає шар ефектів
 * ({@code RevelationBook}). Індекс будується раз на старті у ServiceContainer.
 */
public final class IngredientSourceIndex {

    public enum Kind { CREATURE, FORAGE }

    /** Рід джерела + біоми, де його шукати (без дублікатів, у порядку конфіга). */
    public record Source(Kind kind, List<String> biomes) {
        public Source {
            biomes = List.copyOf(biomes);
        }
    }

    private final Map<String, Source> byIngredient;

    public IngredientSourceIndex(Collection<CreatureDefinition> creatures,
                                 Map<String, List<ForageEntry>> forageBiomes) {
        Map<String, Set<String>> fromCreatures = new LinkedHashMap<>();
        for (CreatureDefinition creature : creatures) {
            if (creature.loot() == null || creature.spawn() == null) continue;
            for (LootItem item : creature.loot().items()) {
                fromCreatures.computeIfAbsent(item.itemId(), id -> new LinkedHashSet<>())
                        .addAll(creature.spawn().naturalBiomes());
            }
        }
        Map<String, Set<String>> fromForage = new LinkedHashMap<>();
        forageBiomes.forEach((biome, entries) -> entries.forEach(entry ->
                fromForage.computeIfAbsent(entry.ingredientId(), id -> new LinkedHashSet<>()).add(biome)));

        Map<String, Source> index = new HashMap<>();
        fromForage.forEach((id, biomes) -> index.put(id, new Source(Kind.FORAGE, List.copyOf(biomes))));
        // Істота перекриває форедж: якщо інгредієнт падає з моба, це головна підказка.
        fromCreatures.forEach((id, biomes) -> index.put(id, new Source(Kind.CREATURE, List.copyOf(biomes))));
        this.byIngredient = Map.copyOf(index);
    }

    /** Порожньо для інгредієнта, який трапляється лише в скринях (global_loot.yml без місця). */
    public Optional<Source> sourceOf(String ingredientId) {
        return Optional.ofNullable(byIngredient.get(ingredientId));
    }
}
