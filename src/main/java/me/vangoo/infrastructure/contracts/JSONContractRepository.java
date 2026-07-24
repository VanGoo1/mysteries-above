package me.vangoo.infrastructure.contracts;

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

/** contracts.json: підписані контракти Sun (ACTIVE/SETTLED/BREACHED). Пишеться після кожної мутації. */
public class JSONContractRepository {

    private static final Logger LOGGER = Logger.getLogger(JSONContractRepository.class.getName());

    public record ContractRecord(String id, String partyA, String partyB, String term,
                                 Map<String, String> params, long createdAtEpochMillis, String state) {}

    public record Model(Map<String, ContractRecord> contracts) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JSONContractRepository(String filePath) {
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
            LOGGER.warning("Failed to load contracts: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save contracts: " + e.getMessage());
        }
    }
}
