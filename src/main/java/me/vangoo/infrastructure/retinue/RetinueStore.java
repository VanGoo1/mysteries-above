package me.vangoo.infrastructure.retinue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.vangoo.domain.valueobjects.RetinueSnapshot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * retinue.json: персистентний почет нежиті власника (Death, Посл. 6). Пишеться після кожної
 * мутації, побитий/відсутній файл → порожньо — каркас {@code WaypointStore}. Див.
 * {@code .claude/rules/lingering-souls.md}.
 */
public class RetinueStore {

    private static final Logger LOGGER = Logger.getLogger(RetinueStore.class.getName());
    private static final Type MODEL_TYPE = new TypeToken<Map<String, RetinueSnapshot>>() {}.getType();

    private final java.io.File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, RetinueSnapshot> byPlayer = new ConcurrentHashMap<>();

    public RetinueStore(String filePath) {
        this.file = new java.io.File(filePath);
        load();
    }

    private void load() {
        if (!file.exists() || file.length() == 0) {
            return;
        }
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            Map<String, RetinueSnapshot> raw = gson.fromJson(reader, MODEL_TYPE);
            if (raw != null) {
                raw.forEach((id, snapshot) -> byPlayer.put(UUID.fromString(id), snapshot));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("Failed to load retinue: " + e.getMessage());
        }
    }

    private void save() {
        Map<String, RetinueSnapshot> raw = new HashMap<>();
        byPlayer.forEach((id, snapshot) -> raw.put(id.toString(), snapshot));
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(raw, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save retinue: " + e.getMessage());
        }
    }

    public Optional<RetinueSnapshot> get(UUID owner) {
        return Optional.ofNullable(byPlayer.get(owner));
    }

    public void put(UUID owner, RetinueSnapshot snapshot) {
        byPlayer.put(owner, snapshot);
        save();
    }

    public void remove(UUID owner) {
        if (byPlayer.remove(owner) != null) {
            save();
        }
    }
}
