package me.vangoo.infrastructure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.dto.BeyonderDTO;
import me.vangoo.infrastructure.mappers.BeyonderMapper;
import me.vangoo.application.services.PathwayManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class JSONBeyonderRepository implements IBeyonderRepository {
    private static final Logger LOGGER = Logger.getLogger(JSONBeyonderRepository.class.getName());

    private final File file;
    private final Gson gson;
    private final Map<UUID, Beyonder> beyonders;
    private final BeyonderMapper mapper;

    public JSONBeyonderRepository(String url, PathwayManager pathwayManager) {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        this.beyonders = new HashMap<>();
        this.mapper = new BeyonderMapper(pathwayManager);

        this.file = new File(url);
        try {
            if (file.createNewFile()) {
                LOGGER.info("Storage file was created: " + url);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to create storage file: " + e.getMessage());
        }

        loadFromFile();
    }

    private void loadFromFile() {
        if (!file.exists() || file.length() == 0) {
            LOGGER.info("No existing data to load");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<UUID, BeyonderDTO>>() {
            }.getType();
            Map<UUID, BeyonderDTO> dtos = gson.fromJson(reader, type);

            if (dtos != null) {
                beyonders.clear();
                for (Map.Entry<UUID, BeyonderDTO> entry : dtos.entrySet()) {
                    try {
                        Beyonder beyonder = mapper.toDomain(entry.getValue());
                        beyonders.put(entry.getKey(), beyonder);
                    } catch (Exception e) {
                        LOGGER.warning("Failed to load beyonder " + entry.getKey() + ": " + e.getMessage());
                    }
                }
                LOGGER.info("Loaded " + beyonders.size() + " beyonders from storage");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load from file: " + e.getMessage());
        }
    }

    private boolean saveToFile() {
        try (FileWriter writer = new FileWriter(file)) {
            // Convert domain models to DTOs
            Map<UUID, BeyonderDTO> dtos = beyonders.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> mapper.toDTO(entry.getValue())
                    ));

            gson.toJson(dtos, writer);
            return true;
        } catch (IOException e) {
            LOGGER.warning("Failed to save to file: " + e.getMessage());
            return false;
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
    public void saveAll() {
        saveToFile();
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

    @Override
    public Map<UUID, Beyonder> getAll() {
        return new HashMap<>(beyonders);
    }
}
