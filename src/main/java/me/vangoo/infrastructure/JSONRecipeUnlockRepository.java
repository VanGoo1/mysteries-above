package me.vangoo.infrastructure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.vangoo.domain.valueobjects.UnlockedRecipe;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

/**
 * Infrastructure: JSON implementation of recipe unlock repository
 */
public class JSONRecipeUnlockRepository implements IRecipeUnlockRepository {
    private static final Logger LOGGER = Logger.getLogger(JSONRecipeUnlockRepository.class.getName());

    private final File file;
    private final Gson gson;
    private final Map<UUID, Set<UnlockedRecipe>> unlockedRecipes;

    public JSONRecipeUnlockRepository(String filePath) {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.unlockedRecipes = new HashMap<>();
        this.file = new File(filePath);

        try {
            if (file.createNewFile()) {
                LOGGER.info("Recipe unlock storage file created: " + filePath);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to create recipe unlock storage file: " + e.getMessage());
        }

        loadFromFile();
    }

    @Override
    public boolean unlockRecipe(UUID playerId, UnlockedRecipe recipe) {
        if (playerId == null || recipe == null) {
            return false;
        }

        Set<UnlockedRecipe> playerRecipes = unlockedRecipes.computeIfAbsent(
                playerId,
                k -> new HashSet<>()
        );

        boolean added = playerRecipes.add(recipe);

        if (added) {
            saveToFile();
            LOGGER.info(String.format("Unlocked recipe for player %s: %s",
                    playerId, recipe));
        }

        return added;
    }

    @Override
    public boolean hasUnlockedRecipe(UUID playerId, String pathwayName, int sequence) {
        if (playerId == null || pathwayName == null) {
            return false;
        }

        Set<UnlockedRecipe> playerRecipes = unlockedRecipes.get(playerId);
        if (playerRecipes == null) {
            return false;
        }

        UnlockedRecipe recipe = UnlockedRecipe.of(pathwayName, sequence);
        return playerRecipes.contains(recipe);
    }

    @Override
    public Set<UnlockedRecipe> getUnlockedRecipes(UUID playerId) {
        if (playerId == null) {
            return Collections.emptySet();
        }

        Set<UnlockedRecipe> recipes = unlockedRecipes.get(playerId);
        return recipes != null ? new HashSet<>(recipes) : Collections.emptySet();
    }

    @Override
    public void saveAll() {
        saveToFile();
    }

    private void loadFromFile() {
        if (!file.exists() || file.length() == 0) {
            LOGGER.info("No existing recipe unlock data to load");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<UUID, HashSet<UnlockedRecipe>>>() {}.getType();
            Map<UUID, Set<UnlockedRecipe>> loaded = gson.fromJson(reader, type);

            if (loaded != null) {
                unlockedRecipes.clear();
                unlockedRecipes.putAll(loaded);
                LOGGER.info("Loaded unlocked recipes for " + unlockedRecipes.size() + " players");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load recipe unlock data: " + e.getMessage());
        }
    }

    private boolean saveToFile() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(unlockedRecipes, writer);
            return true;
        } catch (IOException e) {
            LOGGER.warning("Failed to save recipe unlock data: " + e.getMessage());
            return false;
        }
    }
}