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
                new JSONMembershipRepository.OrderRecord("Door", 9, 999L, 60));
        var model = new JSONMembershipRepository.Model(Map.of(
                "11111111-1111-1111-1111-111111111111",
                new JSONMembershipRepository.PlayerChurchData(member, 0L, true),
                "22222222-2222-2222-2222-222222222222",
                new JSONMembershipRepository.PlayerChurchData(null, 777L, false)));
        repo.save(model);

        var loaded = repo.load().orElseThrow();
        var p1 = loaded.players().get("11111111-1111-1111-1111-111111111111");
        assertEquals("church-evernight", p1.membership().institutionId());
        assertEquals(1, p1.membership().tasks().size());
        assertNull(p1.membership().initiationTask());
        assertEquals(9, p1.membership().activeOrder().sequence());
        assertTrue(p1.initiationUsed());
        assertNull(loaded.players().get("22222222-2222-2222-2222-222222222222").membership());
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
}
