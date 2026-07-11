package me.vangoo.infrastructure.market;

import me.vangoo.infrastructure.market.GatheringSnapshotRepository.EscrowItem;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.ParticipantHome;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure round-trip test: no Bukkit involved, escrow/participant records are plain
 * Strings/primitives, so this exercises the exact JSON shape written on a crash.
 */
class GatheringSnapshotRepositoryTest {

    @Test
    void loadReturnsEmptyWhenNoFileExists(@TempDir File tempDir) {
        GatheringSnapshotRepository repository =
                new GatheringSnapshotRepository(new File(tempDir, "gathering-state.json").getPath());

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void savedSnapshotRoundTripsFaithfully(@TempDir File tempDir) {
        String path = new File(tempDir, "gathering-state.json").getPath();
        GatheringSnapshotRepository writer = new GatheringSnapshotRepository(path);

        Snapshot original = new Snapshot(
                1_720_000_000_000L,
                List.of(
                        new ParticipantHome("player-1", "world", 10.5, 64.0, -20.25, 90.0f, 0.0f),
                        new ParticipantHome("player-2", "world_nether", -5.0, 32.0, 5.0, -45.5f, 12.25f)
                ),
                List.of(
                        new EscrowItem("player-1", "base64-lot-stack"),
                        new EscrowItem("player-2", "base64-order-stack")
                ),
                List.of(
                        new EscrowItem("player-3", "base64-pending-return")
                ),
                List.of("player-4", "player-5"),
                List.of("player-6")
        );

        writer.save(original);

        // Fresh instance to prove it reads from disk, not in-memory state.
        GatheringSnapshotRepository reader = new GatheringSnapshotRepository(path);
        Optional<Snapshot> loaded = reader.load();

        assertTrue(loaded.isPresent());
        assertEquals(original, loaded.get());
    }

    @Test
    void loadReturnsEmptyWhenFileIsEmpty(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "gathering-state.json");
        assertTrue(file.createNewFile());

        GatheringSnapshotRepository repository = new GatheringSnapshotRepository(file.getPath());

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void loadReturnsEmptyWhenFileIsTruncatedJson(@TempDir File tempDir) throws Exception {
        // A crash mid-FileWriter leaves a non-empty but syntactically invalid file.
        File file = new File(tempDir, "gathering-state.json");
        Files.writeString(file.toPath(), "{ \"nextGatheringEpochMillis\": 123,");

        GatheringSnapshotRepository repository = new GatheringSnapshotRepository(file.getPath());

        assertTrue(repository.load().isEmpty());
    }
}
