package me.vangoo.infrastructure.organizations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrderRepositoriesTest {

    @TempDir
    Path dir;

    @Test
    void membershipFileWithoutNewFieldsLoadsWithThemAbsent() throws IOException {
        Path file = dir.resolve("order-memberships.json");
        // Файл «з майбутнього минулого»: без invitations/beyonderKills/pendingRaidLoot.
        Files.writeString(file, """
                {"players":{"11111111-1111-1111-1111-111111111111":
                  {"membership":{"institutionId":"order-aurora","curatorName":"Тінь",
                   "lastTaskRefreshEpochMillis":5,"taskSetsUsed":1,"tasks":[],"favors":null},
                   "rejoinCooldownUntilEpochMillis":0,"abandonedOrders":null}}}
                """);
        JSONOrderMembershipRepository repo = new JSONOrderMembershipRepository(file.toString());
        Optional<JSONOrderMembershipRepository.Model> model = repo.load();
        assertTrue(model.isPresent());
        JSONOrderMembershipRepository.PlayerOrderData data =
                model.get().players().get("11111111-1111-1111-1111-111111111111");
        assertNull(data.invitations());
        assertNull(data.membership().favors());
        assertNull(data.pendingRaidLoot());
        assertFalse(data.falsePapers());
        assertEquals(0, data.beyonderKills());
        assertNull(data.pendingRaidChurch());
    }

    @Test
    void stateRoundTripsAllFourSections() {
        OrderStateRepository repo = new OrderStateRepository(
                dir.resolve("orders-state.json").toString());
        repo.save(new OrderStateRepository.Model(
                Map.of("order-aurora", Map.of("custom:herb", 2)),
                Map.of("order-aurora|church-evernight",
                        new OrderStateRepository.IntelRecord(Map.of("custom:herb", 5), 123L)),
                Map.of("church-evernight", 55L),
                Map.of("church-evernight", 77L)));
        OrderStateRepository.Model loaded = repo.load().orElseThrow();
        assertEquals(2, loaded.stashes().get("order-aurora").get("custom:herb"));
        assertEquals(123L, loaded.intel().get("order-aurora|church-evernight").expiresAtEpochMillis());
        assertEquals(55L, loaded.templeCooldownUntil().get("church-evernight"));
        assertEquals(77L, loaded.priestClosedUntil().get("church-evernight"));
    }

    @Test
    void corruptFilesLoadAsEmpty() throws IOException {
        Path bad = dir.resolve("orders-state.json");
        Files.writeString(bad, "{not json");
        assertTrue(new OrderStateRepository(bad.toString()).load().isEmpty());
    }
}
