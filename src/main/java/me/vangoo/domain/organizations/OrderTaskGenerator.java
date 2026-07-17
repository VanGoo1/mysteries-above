package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Чистий генератор набору завдань ордену. На набір: ≤1 храмова операція
 * (RAID / ASSASSINATE за rank≥TRUSTED), ≤1 шпигунська (RECON / SABOTAGE за
 * rank≥BLADE, лише подвійним агентам, ціль — ВЛАСНА церква агента), решта —
 * чергування HUNT/DELIVER (ворожі групи, фолбек «будь-які» — дзеркало
 * ChurchTaskGenerator).
 */
public final class OrderTaskGenerator {

    public record CreatureCandidate(String creatureId, String pathway, int sequence) {}

    public record IngredientCandidate(String itemKey, String displayName, int sequence) {}

    public record ChurchTarget(String churchId, String churchName) {}

    public List<OrderTask> generate(int count, Institution order,
                                    Map<String, String> pathwayToGroup,
                                    List<CreatureCandidate> creatures,
                                    List<IngredientCandidate> ingredients,
                                    List<ChurchTarget> raidableChurches,
                                    ChurchTarget doubleAgentChurch,
                                    OrderRank rank, Random random) {
        List<OrderTask> tasks = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        // Шпигунська операція — перша претензія на слот (найцінніший контент агента).
        if (doubleAgentChurch != null && tasks.size() < count && random.nextBoolean()) {
            boolean sabotage = rank.atLeast(OrderRank.BLADE) && random.nextBoolean();
            OrderTask spy = sabotage
                    ? OrderTask.sabotage(doubleAgentChurch.churchId(), doubleAgentChurch.churchName())
                    : OrderTask.recon(doubleAgentChurch.churchId(), doubleAgentChurch.churchName());
            if (usedKeys.add(spy.type() + "|" + spy.targetKey())) {
                tasks.add(spy);
            }
        }

        // Храмова операція — одна на набір.
        if (!raidableChurches.isEmpty() && tasks.size() < count && random.nextBoolean()) {
            ChurchTarget target = raidableChurches.get(random.nextInt(raidableChurches.size()));
            boolean assassinate = rank.atLeast(OrderRank.TRUSTED) && random.nextBoolean();
            OrderTask op = assassinate
                    ? OrderTask.assassinate(target.churchId(), target.churchName())
                    : OrderTask.raid(target.churchId(), target.churchName());
            if (usedKeys.add(op.type() + "|" + op.targetKey())) {
                tasks.add(op);
            }
        }

        // Ворожість: групи, покриті доступами ордену, — свої; решта — цілі HUNT.
        Set<String> orderGroups = new HashSet<>();
        for (PathwayAccess access : order.accesses()) {
            String group = pathwayToGroup.get(access.pathwayName());
            if (group != null) {
                orderGroups.add(group);
            }
        }
        List<CreatureCandidate> known = creatures.stream()
                .filter(c -> pathwayToGroup.get(c.pathway()) != null)
                .toList();
        List<CreatureCandidate> foreign = known.stream()
                .filter(c -> !orderGroups.contains(pathwayToGroup.get(c.pathway())))
                .toList();
        List<CreatureCandidate> hostile = new ArrayList<>(foreign.isEmpty() ? known : foreign);
        List<IngredientCandidate> pool = new ArrayList<>(ingredients);

        boolean huntTurn = true;
        while (tasks.size() < count && (!hostile.isEmpty() || !pool.isEmpty())) {
            if ((huntTurn && !hostile.isEmpty()) || pool.isEmpty()) {
                CreatureCandidate c = hostile.remove(random.nextInt(hostile.size()));
                OrderTask t = OrderTask.hunt(c.creatureId(), c.creatureId(), c.sequence());
                if (usedKeys.add(t.type() + "|" + t.targetKey())) {
                    tasks.add(t);
                }
            } else {
                IngredientCandidate i = pool.remove(random.nextInt(pool.size()));
                OrderTask t = OrderTask.deliver(i.itemKey(), i.displayName(), i.sequence());
                if (usedKeys.add(t.type() + "|" + t.targetKey())) {
                    tasks.add(t);
                }
            }
            huntTurn = !huntTurn;
        }
        return tasks;
    }
}
