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
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** orders-state.json: сховища орденів, знання про церкви, кулдауни храмів та священиків. Пишеться після кожної мутації. */
public class OrderStateRepository {

    private static final Logger LOGGER = Logger.getLogger(OrderStateRepository.class.getName());

    public record IntelRecord(Map<String, Integer> manifest, long expiresAtEpochMillis) {}

    public record Model(Map<String, Map<String, Integer>> stashes,          // orderId → itemKey → n
                        Map<String, IntelRecord> intel,                      // "orderId|churchId" → знімок
                        Map<String, Long> templeCooldownUntil,               // churchId → epoch millis
                        Map<String, Long> priestClosedUntil) {}              // churchId → epoch millis

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public OrderStateRepository(String filePath) {
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
            LOGGER.warning("Failed to load orders state: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save orders state: " + e.getMessage());
        }
    }
}
