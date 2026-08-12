package me.vangoo.infrastructure.organizations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * shrines.json: де саме стоїть святиня кожної церкви. По одній на церкву на весь світ.
 *
 * <p>Раніше шрайни сховища не мали — їхньою пам'яттю була сама мітка у світі. Це
 * перестало працювати, щойно з'явилась вимога унікальності: щоб не поставити другу
 * святиню Сонця, треба знати про першу, а мітки в незавантажених чанках не перелічити.
 * Тому реєстр, і пишеться він після кожної постановки.
 */
public class ShrineRegistry {

    private static final Logger LOGGER = Logger.getLogger(ShrineRegistry.class.getName());

    public record Placed(String institutionId, String world, int x, int y, int z) {}

    public record Model(List<Placed> shrines) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Placed> placed = new ArrayList<>();

    public ShrineRegistry(String filePath) {
        this.file = new File(filePath);
        load().ifPresent(model -> {
            if (model.shrines() != null) {
                placed.addAll(model.shrines());
            }
        });
    }

    public boolean has(String institutionId) {
        return placed.stream().anyMatch(p -> p.institutionId().equals(institutionId));
    }

    public List<String> placedChurches() {
        return placed.stream().map(Placed::institutionId).toList();
    }

    public Optional<Location> locationOf(String institutionId, World world) {
        return placed.stream()
                .filter(p -> p.institutionId().equals(institutionId))
                .filter(p -> p.world().equals(world.getName()))
                .findFirst()
                .map(p -> new Location(world, p.x(), p.y(), p.z()));
    }

    /**
     * Чи стоїть уже святиня поблизу. Село розтягнуте на кілька чанків, і кожен із них
     * приходить своєю подією — без цієї перевірки одне село отримало б кілька святинь
     * різних церков.
     */
    public boolean hasNear(Location loc, double radius) {
        if (loc.getWorld() == null) {
            return false;
        }
        String world = loc.getWorld().getName();
        double squared = radius * radius;
        for (Placed p : placed) {
            if (!p.world().equals(world)) {
                continue;
            }
            double dx = p.x() - loc.getX();
            double dz = p.z() - loc.getZ();
            if (dx * dx + dz * dz <= squared) {
                return true;
            }
        }
        return false;
    }

    public void add(String institutionId, Location loc) {
        placed.removeIf(p -> p.institutionId().equals(institutionId));
        placed.add(new Placed(institutionId, loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        save();
    }

    private Optional<Model> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            return Optional.ofNullable(gson.fromJson(reader, Model.class));
        } catch (IOException | JsonSyntaxException e) {
            // Побитий файл читаємо як «жодної святині ще немає». Гірший сценарій —
            // зірваний старт плагіна; наслідок цього — щонайбільше друга святиня церкви.
            LOGGER.warning("Failed to load shrines: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void save() {
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(new Model(List.copyOf(placed)), writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save shrines: " + e.getMessage());
        }
    }
}
