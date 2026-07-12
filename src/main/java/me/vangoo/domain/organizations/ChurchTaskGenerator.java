package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Чистий генератор пулу завдань церкви: ротація HUNT/DELIVER без дублів цілей. */
public final class ChurchTaskGenerator {

    public record CreatureCandidate(String creatureId, String pathway, int sequence) {}

    public record IngredientCandidate(String itemKey, String displayName, int sequence) {}

    public List<ChurchTask> generate(int count, Institution church,
                                     Map<String, String> pathwayToGroup,
                                     List<CreatureCandidate> creatures,
                                     List<IngredientCandidate> ingredients,
                                     Random random) {
        Set<String> churchGroups = new HashSet<>();
        for (PathwayAccess access : church.accesses()) {
            String group = pathwayToGroup.get(access.pathwayName());
            if (group != null) {
                churchGroups.add(group);
            }
        }
        List<CreatureCandidate> hostile = new ArrayList<>(creatures.stream()
                .filter(c -> {
                    String group = pathwayToGroup.get(c.pathway());
                    return group != null && !churchGroups.contains(group);
                }).toList());
        List<IngredientCandidate> pool = new ArrayList<>(ingredients);

        List<ChurchTask> tasks = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        boolean huntTurn = true;
        while (tasks.size() < count && (!hostile.isEmpty() || !pool.isEmpty())) {
            if ((huntTurn && !hostile.isEmpty()) || pool.isEmpty()) {
                CreatureCandidate c = hostile.remove(random.nextInt(hostile.size()));
                if (usedKeys.add(c.creatureId())) {
                    tasks.add(ChurchTask.hunt(c.creatureId(), c.creatureId(), c.sequence()));
                }
            } else {
                IngredientCandidate i = pool.remove(random.nextInt(pool.size()));
                if (usedKeys.add(i.itemKey())) {
                    tasks.add(ChurchTask.deliver(i.itemKey(), i.displayName(), i.sequence()));
                }
            }
            huntTurn = !huntTurn;
        }
        return tasks;
    }

    /** Ініціація: найслабша (max sequence) істота з кандидатів; порожньо, якщо кандидатів нема. */
    public Optional<ChurchTask> generateInitiation(List<CreatureCandidate> creatures,
                                                   Random random) {
        return creatures.stream()
                .max(Comparator.comparingInt(CreatureCandidate::sequence))
                .map(c -> ChurchTask.initiationHunt(c.creatureId(), c.creatureId()));
    }
}
