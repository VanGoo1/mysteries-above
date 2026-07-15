package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ChurchTaskGeneratorTest {

    private final ChurchTaskGenerator generator = new ChurchTaskGenerator();

    private final Institution church = new Institution("church-knowledge-wisdom",
            InstitutionType.CHURCH, "Церква Бога Знань та Мудрості", "лор",
            List.of(PathwayAccess.full("WhiteTower")));

    // WhiteTower → GodAlmighty; Error → LordOfMysteries (ворожа група)
    private final Map<String, String> groups = Map.of(
            "WhiteTower", "GodAlmighty", "Visionary", "GodAlmighty",
            "Error", "LordOfMysteries", "Door", "LordOfMysteries");

    private final List<ChurchTaskGenerator.CreatureCandidate> creatures = List.of(
            new ChurchTaskGenerator.CreatureCandidate("error_sphinx_9", "Error", 9),
            new ChurchTaskGenerator.CreatureCandidate("error_wyrm_6", "Error", 6),
            new ChurchTaskGenerator.CreatureCandidate("visionary_owl_9", "Visionary", 9));

    private final List<ChurchTaskGenerator.IngredientCandidate> ingredients = List.of(
            new ChurchTaskGenerator.IngredientCandidate("custom:night_vanilla", "Нічна ваніль", 9),
            new ChurchTaskGenerator.IngredientCandidate("custom:lava_lily", "Лавова лілія", 6));

    @Test
    void huntsOnlyHostileGroups() {
        List<ChurchTask> tasks = generator.generate(4, church, groups, creatures,
                List.of(), new Random(42));
        assertFalse(tasks.isEmpty());
        for (ChurchTask t : tasks) {
            assertEquals(ChurchTask.Type.HUNT, t.type());
            assertNotEquals("visionary_owl_9", t.targetKey()); // своя група — не ціль
        }
    }

    /**
     * Церква Блазня через підгрупи покриває всі групи, що мають істот, тож фільтр
     * «чужа група» лишав порожній пул і гравець бачив самі доставки. Фолбек дзеркалить
     * {@code ChurchDuelService.pickOpponent}: нема чужих — полюємо на будь-яких.
     */
    @Test
    void fallsBackToAnyCreatureWhenChurchDomainCoversEveryCreatureGroup() {
        Institution wideChurch = new Institution("church-fool",
                InstitutionType.CHURCH, "Церква Блазня", "лор",
                List.of(PathwayAccess.full("Error"), PathwayAccess.full("WhiteTower")));

        List<ChurchTask> tasks = generator.generate(2, wideChurch, groups, creatures,
                ingredients, new Random(7));

        assertTrue(tasks.stream().anyMatch(t -> t.type() == ChurchTask.Type.HUNT),
                "церква без чужих груп мусить діставати HUNT із фолбеку, а не самі доставки");
    }

    @Test
    void noDuplicateTargetsAndCountRespected() {
        List<ChurchTask> tasks = generator.generate(2, church, groups, creatures,
                ingredients, new Random(1));
        assertEquals(2, tasks.size());
        Set<String> keys = tasks.stream().map(ChurchTask::targetKey).collect(Collectors.toSet());
        assertEquals(2, keys.size());
    }

    @Test
    void strongerTargetPaysMore() {
        // seq 6 (сильніша) проти seq 9: нагорода за одиницю більша
        ChurchTask strong = ChurchTask.hunt("error_wyrm_6", "?", 6);
        ChurchTask weak = ChurchTask.hunt("error_sphinx_9", "?", 9);
        assertTrue(strong.rewardPoints() / strong.required()
                > weak.rewardPoints() / weak.required());
    }

    @Test
    void progressAndCompletion() {
        ChurchTask t = ChurchTask.deliver("custom:night_vanilla", "Нічна ваніль", 9);
        assertFalse(t.isComplete());
        ChurchTask done = t.withProgress(t.required());
        assertTrue(done.isComplete());
    }
}
