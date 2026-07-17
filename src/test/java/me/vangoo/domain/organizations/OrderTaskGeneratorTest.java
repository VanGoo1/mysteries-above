package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OrderTaskGeneratorTest {

    private final OrderTaskGenerator generator = new OrderTaskGenerator();
    private final Institution order = new Institution("order-aurora",
            InstitutionType.SECRET_ORDER, "Орден Аврори", "лор",
            List.of(PathwayAccess.full("Door")));
    private final Map<String, String> groups = Map.of(
            "Door", "LordOfMysteries", "door", "LordOfMysteries",
            "WhiteTower", "DemonOfKnowledge", "whitetower", "DemonOfKnowledge");
    private final List<OrderTaskGenerator.CreatureCandidate> creatures = List.of(
            new OrderTaskGenerator.CreatureCandidate("wt_mob", "whitetower", 9),
            new OrderTaskGenerator.CreatureCandidate("door_mob", "door", 9));
    private final List<OrderTaskGenerator.IngredientCandidate> ingredients = List.of(
            new OrderTaskGenerator.IngredientCandidate("custom:herb", "Трава", 9));
    private final OrderTaskGenerator.ChurchTarget church =
            new OrderTaskGenerator.ChurchTarget("church-evernight", "Богині Вічної Ночі");

    @Test
    void spyTasksOnlyForDoubleAgents() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(church), null, OrderRank.MAGISTER, new Random(seed));
            assertTrue(tasks.stream().noneMatch(OrderTask::isSpyOp), "seed=" + seed);
        }
    }

    @Test
    void doubleAgentGetsAtMostOneSpyTaskTargetingOwnChurch() {
        boolean seenSpy = false;
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(church), church, OrderRank.MAGISTER, new Random(seed));
            long spies = tasks.stream().filter(OrderTask::isSpyOp).count();
            assertTrue(spies <= 1);
            seenSpy |= spies == 1;
            tasks.stream().filter(OrderTask::isSpyOp)
                    .forEach(t -> assertEquals("church-evernight", t.targetKey()));
        }
        assertTrue(seenSpy, "spy task never generated across seeds");
    }

    @Test
    void noTempleOpsWithoutRaidableChurches() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(), null, OrderRank.HIDDEN_LORD, new Random(seed));
            assertTrue(tasks.stream().noneMatch(OrderTask::isTempleOp), "seed=" + seed);
        }
    }

    @Test
    void assassinationRequiresTrustedRank() {
        for (int seed = 0; seed < 60; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(church), null, OrderRank.PAWN, new Random(seed));
            assertTrue(tasks.stream().noneMatch(t -> t.type() == OrderTask.Type.ASSASSINATE),
                    "seed=" + seed);
        }
    }

    @Test
    void huntPrefersForeignGroupsWithFallback() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(), null, OrderRank.PAWN, new Random(seed));
            tasks.stream().filter(t -> t.type() == OrderTask.Type.HUNT)
                    .forEach(t -> assertEquals("wt_mob", t.targetKey())); // чужа група
        }
    }

    @Test
    void noDuplicateTargets() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(6, order, groups, creatures, ingredients,
                    List.of(church), church, OrderRank.MAGISTER, new Random(seed));
            long distinct = tasks.stream().map(t -> t.type() + "|" + t.targetKey()).distinct().count();
            assertEquals(tasks.size(), distinct, "seed=" + seed);
        }
    }
}
