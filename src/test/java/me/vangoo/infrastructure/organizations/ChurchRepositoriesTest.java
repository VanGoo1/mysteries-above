package me.vangoo.infrastructure.organizations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChurchRepositoriesTest {

    @TempDir
    Path dir;

    @Test
    void membershipRoundTripsAndKeepsNullables() {
        var repo = new JSONMembershipRepository(dir.resolve("memberships.json").toString());
        assertTrue(repo.load().isEmpty()); // нема файлу — порожньо

        var task = new JSONMembershipRepository.TaskRecord(
                "HUNT", "error_sphinx_9", "error_sphinx_9", 4, 1, 48);
        var member = new JSONMembershipRepository.MembershipRecord(
                "church-evernight", 250, 100, 123L, List.of(task), null, null,
                new JSONMembershipRepository.OrderRecord("Door", 9, 999L, 60),
                List.of("Door:9", "Door:8"));
        var model = new JSONMembershipRepository.Model(Map.of(
                "11111111-1111-1111-1111-111111111111",
                new JSONMembershipRepository.PlayerChurchData(member, 0L, true, false),
                "22222222-2222-2222-2222-222222222222",
                new JSONMembershipRepository.PlayerChurchData(null, 777L, false, false)));
        repo.save(model);

        var loaded = repo.load().orElseThrow();
        var p1 = loaded.players().get("11111111-1111-1111-1111-111111111111");
        assertEquals("church-evernight", p1.membership().institutionId());
        assertEquals(1, p1.membership().tasks().size());
        assertNull(p1.membership().initiationTask());
        assertEquals(9, p1.membership().activeOrder().sequence());
        assertEquals(List.of("Door:9", "Door:8"), p1.membership().orderedPotions());
        assertTrue(p1.initiationUsed());
        assertNull(loaded.players().get("22222222-2222-2222-2222-222222222222").membership());
    }

    /** memberships.json, записаний до появи orderedPotions, мусить читатись як порожня історія. */
    @Test
    void membershipWithoutOrderedPotionsFieldLoadsAsEmptyHistory() throws IOException {
        Path file = dir.resolve("memberships.json");
        Files.writeString(file, """
                {"players":{"11111111-1111-1111-1111-111111111111":{
                  "membership":{"institutionId":"church-evernight","lifetimeContribution":250,
                    "balance":100,"lastTaskRefreshEpochMillis":123,"tasks":[]},
                  "rejoinCooldownUntilEpochMillis":0,"initiationUsed":false,"trialPassed":false}}}
                """);
        var loaded = new JSONMembershipRepository(file.toString()).load().orElseThrow();
        var membership = loaded.players().get("11111111-1111-1111-1111-111111111111").membership();
        assertNull(membership.orderedPotions());

        // а Membership.restoreOrderedPotionKeys(null) перетворює цей null на порожню історію
        var restored = new me.vangoo.domain.organizations.Membership(
                java.util.UUID.randomUUID(), membership.institutionId());
        restored.restoreOrderedPotionKeys(membership.orderedPotions());
        assertTrue(restored.orderedPotionKeys().isEmpty());
    }

    @Test
    void sitesAndVaultsRoundTrip() {
        var sites = new ChurchSiteRepository(dir.resolve("church-sites.json").toString());
        sites.save(new ChurchSiteRepository.Model(
                List.of(new ChurchSiteRepository.Site("church-fool", "world", 1, 65, 2, 0f, 0f)),
                List.of("world:100:200")));
        var loadedSites = sites.load().orElseThrow();
        assertEquals("church-fool", loadedSites.sites().get(0).institutionId());
        assertEquals(List.of("world:100:200"), loadedSites.processedVillageKeys());

        var state = new ChurchStateRepository(dir.resolve("churches-state.json").toString());
        state.save(new ChurchStateRepository.Model(
                Map.of("church-fool", Map.of("custom:night_vanilla", 6, "recipe:Door:9", 1))));
        assertEquals(6, state.load().orElseThrow()
                .vaults().get("church-fool").get("custom:night_vanilla"));
    }

    @Test
    void corruptFileIsIgnored() throws IOException {
        Path file = dir.resolve("memberships.json");
        Files.writeString(file, "{broken json");
        assertTrue(new JSONMembershipRepository(file.toString()).load().isEmpty());
    }

    @Test
    void ukrainianTaskNamePersistsAsUtf8() throws IOException {
        Path file = dir.resolve("memberships.json");
        var repo = new JSONMembershipRepository(file.toString());
        var task = new JSONMembershipRepository.TaskRecord(
                "DELIVER", "custom:night_vanilla", "Нічна ваніль", 6, 0, 8);
        var member = new JSONMembershipRepository.MembershipRecord(
                "church-evernight", 0, 0, 0L, List.of(task), null, null, null, List.of());
        repo.save(new JSONMembershipRepository.Model(Map.of(
                "11111111-1111-1111-1111-111111111111",
                new JSONMembershipRepository.PlayerChurchData(member, 0L, false, false))));

        // round-trip preserves the Cyrillic display name
        var loaded = repo.load().orElseThrow();
        assertEquals("Нічна ваніль", loaded.players()
                .get("11111111-1111-1111-1111-111111111111").membership().tasks().get(0).targetName());
        // and the file is written as UTF-8 regardless of the JVM default charset
        String utf8 = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(utf8.contains("Нічна ваніль"), "targetName must be persisted as UTF-8");
    }
}
