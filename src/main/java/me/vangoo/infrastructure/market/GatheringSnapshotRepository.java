package me.vangoo.infrastructure.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * gathering-state.json: час наступного збору + активний ескроу + черга повернень.
 * Пишеться після кожної мутації сесії (обсяг малий — синхронний запис прийнятний).
 * Мета — краш-безпека: після рестарту все з ескроу повертається власникам.
 */
public class GatheringSnapshotRepository {

    private static final Logger LOGGER = Logger.getLogger(GatheringSnapshotRepository.class.getName());

    public record ParticipantHome(String playerId, String world,
                                   double x, double y, double z, float yaw, float pitch) {}

    public record EscrowItem(String ownerId, String base64Stack) {}

    public record Snapshot(long nextGatheringEpochMillis,
                            List<ParticipantHome> participants,
                            List<EscrowItem> escrow,
                            List<EscrowItem> pendingReturns,
                            List<String> bannedFromNext) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public GatheringSnapshotRepository(String filePath) {
        this.file = new File(filePath);
    }

    public Optional<Snapshot> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (FileReader reader = new FileReader(file)) {
            return Optional.ofNullable(gson.fromJson(reader, Snapshot.class));
        } catch (IOException e) {
            LOGGER.warning("Failed to load gathering snapshot: " + e.getMessage());
            return Optional.empty();
        } catch (JsonSyntaxException e) {
            // A crash mid-write leaves a truncated/corrupt file; ignore it and recover
            // as if there were no snapshot rather than crashing plugin boot.
            LOGGER.warning("Corrupt gathering snapshot ignored: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Snapshot snapshot) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(snapshot, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save gathering snapshot: " + e.getMessage());
        }
    }

    /** Paper ItemStack#serializeAsBytes — стабільний бінарний формат із міграцією версій. */
    public static String encodeStack(ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    public static ItemStack decodeStack(String base64) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
    }
}
