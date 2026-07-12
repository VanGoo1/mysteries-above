package me.vangoo.infrastructure.organizations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** church-sites.json: місце розташування церков і оброблені village-ключі. Пишеться після кожної мутації. */
public class ChurchSiteRepository {

    private static final Logger LOGGER = Logger.getLogger(ChurchSiteRepository.class.getName());

    public record Site(String institutionId, String world, double x, double y, double z,
                       float yaw, float pitch) {}

    public record Model(List<Site> sites, List<String> processedVillageKeys) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ChurchSiteRepository(String filePath) {
        this.file = new File(filePath);
    }

    public Optional<Model> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (FileReader reader = new FileReader(file)) {
            return Optional.ofNullable(gson.fromJson(reader, Model.class));
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warning("Failed to load church sites: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save church sites: " + e.getMessage());
        }
    }
}
