package me.vangoo.infrastructure.organizations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** order-memberships.json: членства таємних орденів, запрошення, кулдауни. Пишеться після кожної мутації. */
public class JSONOrderMembershipRepository {

    private static final Logger LOGGER = Logger.getLogger(JSONOrderMembershipRepository.class.getName());

    public record TaskRecord(String type, String weight, String targetKey, String targetName,
                             int required, int progress) {}

    public record FavorRecord(String weight, long earnedAtEpochMillis) {}

    public record MembershipRecord(String institutionId, String curatorName,
                                   long lastTaskRefreshEpochMillis, int taskSetsUsed,
                                   List<TaskRecord> tasks, List<FavorRecord> favors) {}

    public record InvitationRecord(String institutionId, String reason, long createdAtEpochMillis) {}

    /**
     * pendingRaidLoot — тека від рейдів; null у старих файлах = порожня тека.
     * falsePapers — флаг дезертирства; у старих файлах = false.
     */
    public record PlayerOrderData(MembershipRecord membership,
                                  long rejoinCooldownUntilEpochMillis,
                                  List<String> abandonedOrders,
                                  List<InvitationRecord> invitations,
                                  int beyonderKills,
                                  long talismanReissueAfterEpochMillis,
                                  Map<String, Integer> pendingRaidLoot,
                                  boolean falsePapers) {}

    public record Model(Map<String, PlayerOrderData> players) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JSONOrderMembershipRepository(String filePath) {
        this.file = new File(filePath);
    }

    public Optional<Model> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            return Optional.ofNullable(gson.fromJson(reader, Model.class));
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warning("Failed to load order memberships: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save order memberships: " + e.getMessage());
        }
    }
}
