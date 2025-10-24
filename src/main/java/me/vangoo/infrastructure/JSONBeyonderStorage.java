package me.vangoo.infrastructure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.vangoo.domain.Beyonder;
import me.vangoo.domain.Pathway;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class JSONBeyonderStorage implements IBeyonderStorage {
    private File file;
    private final Gson gson;
    private final Map<UUID, Beyonder> beyonders;
    private PathwayAdapter pathwayAdapter;

    public JSONBeyonderStorage(String url, PathwayAdapter pathwayAdapter) {
        this.pathwayAdapter = pathwayAdapter;
        gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Pathway.class, pathwayAdapter).create();
        beyonders = new HashMap<>();

        file = new File(url);
        try {
            if (file.createNewFile()) {
                Logger.getLogger(JSONBeyonderStorage.class.getName()).warning("Storage file was created");
            }
        } catch (IOException e) {
            Logger.getLogger(JSONBeyonderStorage.class.getName()).warning(e.getMessage());
        }
        loadFromFile();
    }

    private boolean saveToFile() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(beyonders, writer);
            return true;
        } catch (IOException e) {
            Logger.getLogger(JSONBeyonderStorage.class.getName()).warning(e.getMessage());
            return false;
        }
    }

    private void loadFromFile() {
        if (!file.exists() || file.length() == 0) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<UUID, Beyonder>>() {
            }.getType();
            Map<UUID, Beyonder> loaded = gson.fromJson(reader, type);

            if (loaded != null) {
                beyonders.clear();
                beyonders.putAll(loaded);
            }
        } catch (IOException e) {
            Logger.getLogger(JSONBeyonderStorage.class.getName()).warning(e.getMessage());
        }
    }

    @Override
    public boolean add(Beyonder beyonder) {
        if (beyonder == null || beyonder.getPlayerId() == null) {
            return false;
        }
        beyonders.put(beyonder.getPlayerId(), beyonder);
        return saveToFile();
    }

    @Override
    public boolean remove(UUID playerId) {
        if (playerId == null || !beyonders.containsKey(playerId)) {
            return false;
        }
        beyonders.remove(playerId);
        return saveToFile();
    }

    @Override
    public Beyonder get(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return beyonders.get(playerId);
    }

    @Override
    public boolean update(UUID playerId, Beyonder beyonder) {
        if (playerId == null || !beyonders.containsKey(playerId) || beyonder == null) {
            return false;
        }
        beyonders.put(playerId, beyonder);
        return saveToFile();
    }

    public Map<UUID, Beyonder> getAll() {
        return beyonders;
    }
}
