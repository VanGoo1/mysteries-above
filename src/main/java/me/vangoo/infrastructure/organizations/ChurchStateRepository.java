package me.vangoo.infrastructure.organizations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** churches-state.json: вміст сховищ церков (institutionId -> itemKey -> кількість). Пишеться після кожної мутації. */
public class ChurchStateRepository {

    private static final Logger LOGGER = Logger.getLogger(ChurchStateRepository.class.getName());

    public record Model(Map<String, Map<String, Integer>> vaults) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ChurchStateRepository(String filePath) {
        this.file = new File(filePath);
    }

    public Optional<Model> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (FileReader reader = new FileReader(file)) {
            return Optional.ofNullable(gson.fromJson(reader, Model.class));
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warning("Failed to load church state: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save church state: " + e.getMessage());
        }
    }
}
