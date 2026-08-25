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
import java.util.Optional;
import java.util.logging.Logger;

/** church-sites.json: місце розташування церков і оброблені village-ключі. Пишеться після кожної мутації. */
public class ChurchSiteRepository {

    private static final Logger LOGGER = Logger.getLogger(ChurchSiteRepository.class.getName());

    /**
     * Габарити будівлі відносно мітки священика: `half` — квадратна півширина по XZ
     * (квадратна, бо worldgen повертає структуру випадково), `down`/`up` — скільки
     * блоків будівля займає під міткою й над нею. Потрібні лише для захисту від
     * поламки. `null` у записі — нормально: сайт, прив'язаний вручну (`/church bind`)
     * або записаний до появи захисту, просто не боронить блоки.
     */
    public record Box(int half, int down, int up) {}

    public record Site(String institutionId, String world, double x, double y, double z,
                       float yaw, float pitch, Box box) {}

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
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            return Optional.ofNullable(gson.fromJson(reader, Model.class));
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warning("Failed to load church sites: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save church sites: " + e.getMessage());
        }
    }
}
