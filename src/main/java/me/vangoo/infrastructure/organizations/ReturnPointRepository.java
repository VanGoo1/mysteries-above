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

/**
 * church-returns.json: звідки гравця забрав шрайн. Пишеться після кожної мутації
 * (запис на візит, і той короткий) — каркас узято з
 * {@link me.vangoo.infrastructure.market.GatheringSnapshotRepository}.
 *
 * <p>Чому персистимо, хоч дуель ({@code ChurchDuelService}) тримає таке в пам'яті сесії:
 * дуель — рідкісна подія на кілька хвилин, а візит до храму рутинний, і рестарт сервера
 * не має розкидати всіх відвідувачів по спавну головного світу.
 */
public class ReturnPointRepository {

    private static final Logger LOGGER = Logger.getLogger(ReturnPointRepository.class.getName());

    public record Point(String playerId, String world,
                        double x, double y, double z, float yaw, float pitch) {}

    public record Model(List<Point> points) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ReturnPointRepository(String filePath) {
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
            // Обрив на записі лишає побитий файл — гірше за втрачені точки повернення був
            // би зірваний старт плагіна, тож читаємо як «нікого в храмі немає».
            LOGGER.warning("Failed to load church return points: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save church return points: " + e.getMessage());
        }
    }
}
