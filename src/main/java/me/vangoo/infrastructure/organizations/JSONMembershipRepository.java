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

/** memberships.json: членства, кулдаун повторного вступу, флаг ініціації. Пишеться після кожної мутації. */
public class JSONMembershipRepository {

    private static final Logger LOGGER = Logger.getLogger(JSONMembershipRepository.class.getName());

    public record TaskRecord(String type, String targetKey, String targetName,
                             int required, int progress, int rewardPoints) {}

    public record OrderRecord(String pathwayName, int sequence,
                              long readyAtEpochMillis, int pointsPaid) {}

    /** orderedPotions — ключі "<шлях>:<посл.>"; null у файлах до появи поля = порожня історія. */
    public record MembershipRecord(String institutionId, int lifetimeContribution, int balance,
                                   long lastTaskRefreshEpochMillis, List<TaskRecord> tasks,
                                   TaskRecord initiationTask, String initiationPathway,
                                   OrderRecord activeOrder, List<String> orderedPotions) {}

    public record PlayerChurchData(MembershipRecord membership,
                                   long rejoinCooldownUntilEpochMillis,
                                   boolean initiationUsed,
                                   boolean trialPassed) {}

    public record Model(Map<String, PlayerChurchData> players) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JSONMembershipRepository(String filePath) {
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
            LOGGER.warning("Failed to load memberships: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save memberships: " + e.getMessage());
        }
    }
}
