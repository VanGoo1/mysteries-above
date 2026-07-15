package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        // Істота з нерозпізнаним шляхом — завжди поза грою (дірка в даних, не ціль).
        List<CreatureCandidate> known = creatures.stream()
                .filter(c -> pathwayToGroup.get(c.pathway()) != null)
                .toList();
        List<CreatureCandidate> foreign = known.stream()
                .filter(c -> !churchGroups.contains(pathwayToGroup.get(c.pathway())))
                .toList();
        // Домен церкви може покривати всі групи, що мають істот (Церква Блазня — саме
        // такий випадок), і тоді чужих не лишається. Без фолбеку пул HUNT порожній
        // назавжди й гравець бачить самі доставки. Дзеркалить ChurchDuelService.pickOpponent.
        List<CreatureCandidate> hostile = new ArrayList<>(foreign.isEmpty() ? known : foreign);
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
}
