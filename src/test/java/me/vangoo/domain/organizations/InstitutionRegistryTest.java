package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class InstitutionRegistryTest {

    private final InstitutionRegistry registry = new InstitutionRegistry();

    @Test
    void hasTenChurchesAndAtLeastTwentyFiveOrders() {
        assertEquals(10, registry.churches().size());
        assertTrue(registry.all().size() - registry.churches().size() >= 25);
    }

    @Test
    void idsAreUnique() {
        Set<String> ids = registry.all().stream()
                .map(Institution::id).collect(Collectors.toSet());
        assertEquals(registry.all().size(), ids.size());
    }

    @Test
    void everyImplementedPathwayIsCoveredByAChurchOrOrder() {
        // Наявні шляхи плагіна (ключі PathwayManager). Error — свідомо лише таємна організація.
        for (String pathway : List.of("Fool", "Door", "Justiciar", "Visionary", "WhiteTower")) {
            assertFalse(registry.churchesAccepting(pathway).isEmpty(),
                    "no church accepts " + pathway);
        }
        assertTrue(registry.all().stream()
                .anyMatch(i -> i.accessFor("Error").isPresent()), "Error not covered at all");
    }

    @Test
    void pinnedCanonAccessesAreCorrect() {
        Institution evernight = registry.byId("church-evernight").orElseThrow();
        assertEquals(3, evernight.accessFor("Fool").orElseThrow().minSequence());

        Institution storms = registry.byId("church-lord-of-storms").orElseThrow();
        assertEquals(7, storms.accessFor("Visionary").orElseThrow().minSequence());

        Institution fool = registry.byId("church-fool").orElseThrow();
        assertTrue(fool.accessFor("Door").orElseThrow().isFull());
        assertEquals("Сім'я Авраам", fool.accessFor("Door").orElseThrow().branch());
        assertEquals(2, fool.accessFor("Justiciar").orElseThrow().minSequence());

        Institution wisdom = registry.byId("church-knowledge-wisdom").orElseThrow();
        assertTrue(wisdom.accessFor("WhiteTower").orElseThrow().isFull());
    }

    @Test
    void pathlessPlayerIsAcceptedByEveryChurch() {
        assertEquals(10, registry.churchesAccepting(null).size());
    }

    @Test
    void stubOnlyChurchesAreTheKnownSet() {
        // Ініціація духа (дуель, ChurchService.initiationPathwayChoices) обирає ЛИШЕ
        // серед РЕАЛІЗОВАНИХ шляхів (шлях без зареєстрованих здібностей — ChurchService.hasAnyAbility) — інакше
        // гравець отримав би безвихідний, назавжди непрогресуючий шлях (без здібностей
        // і без рецептів варіння). Церкви нижче присвячені канонічним шляхам, які поки
        // що є лише стабами (Sun, Darkness, Death, Tyrant, Paragon, Hermit, RedPriest,
        // Demoness тощо) — для них ChurchService.canStartTrial свідомо ховає кнопку
        // випробування, доки шлях не реалізований по-справжньому. Цей тест фіксує саме
        // цей список: якщо він випадково зміниться (нова стаб-лише церква або, навпаки,
        // одна з цих церков отримає доступ до реального шляху), тест приверне увагу,
        // а не мовчки створить нову діру для ChurchService.
        Set<String> realPathways = Set.of("Error", "Visionary", "Door", "Justiciar", "WhiteTower", "Fool");
        Set<String> stubOnlyChurchIds = registry.churches().stream()
                .filter(church -> church.accesses().stream()
                        .map(PathwayAccess::pathwayName)
                        .noneMatch(realPathways::contains))
                .map(Institution::id)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "church-god-of-combat", "church-earth-mother", "church-eternal-sun",
                "church-steam-machinery", "church-eternal-darkness", "church-ruler-of-calamity"),
                stubOnlyChurchIds);
    }
}
