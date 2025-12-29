package me.vangoo.infrastructure.structures;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Infrastructure: Loads and places NBT structures using Bukkit Structure API
 * No external dependencies required - uses native Minecraft structure format
 */
public class NBTStructureLoader {
    private static final Logger LOGGER = Logger.getLogger(NBTStructureLoader.class.getName());
    private static final Random RANDOM = new Random();

    private final Plugin plugin;
    private final File structuresDirectory;
    private final StructureManager structureManager;
    private final Map<String, Structure> loadedStructures;

    public NBTStructureLoader(Plugin plugin, File structuresDirectory) {
        this.plugin = plugin;
        this.structuresDirectory = structuresDirectory;
        this.structureManager = Bukkit.getStructureManager();
        this.loadedStructures = new HashMap<>();

        if (!structuresDirectory.exists()) {
            structuresDirectory.mkdirs();
            LOGGER.info("Created structures directory: " + structuresDirectory.getPath());
        }
    }

    /**
     * Load structure from NBT file and cache it
     */
    public Structure loadStructure(String fileName) throws IOException {
        // Check cache first
        if (loadedStructures.containsKey(fileName)) {
            return loadedStructures.get(fileName);
        }

        File file = new File(structuresDirectory, fileName);

        if (!file.exists()) {
            throw new IOException("Structure file not found: " + fileName);
        }

        // Create namespaced key for structure
        String keyName = fileName.replace(".nbt", "").toLowerCase();
        NamespacedKey key = new NamespacedKey(plugin, keyName);

        try {
            // Load structure from file using StructureManager
            Structure structure;

            try (FileInputStream fis = new FileInputStream(file)) {
                structure = structureManager.loadStructure(fis);
            }

            // Register the loaded structure
            Structure registeredStructure = structureManager.registerStructure(key, structure);

            if (registeredStructure == null) {
                LOGGER.warning("Structure registered but returned null, using loaded structure");
                registeredStructure = structure;
            }

            // Cache structure
            loadedStructures.put(fileName, registeredStructure);

            LOGGER.info("Loaded structure: " + fileName + " (size: " +
                    registeredStructure.getSize().getBlockX() + "x" +
                    registeredStructure.getSize().getBlockY() + "x" +
                    registeredStructure.getSize().getBlockZ() + ")");

            return registeredStructure;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load structure " + fileName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Place structure at location with default settings
     */
    public void pasteStructure(Structure structure, Location location) {
        pasteStructure(structure, location, true, StructureRotation.NONE, Mirror.NONE, -1, 1.0f);
    }

    /**
     * Place structure at location with custom settings
     *
     * @param structure Structure to place
     * @param location Target location
     * @param includeEntities Whether to spawn entities from structure
     * @param rotation Rotation to apply
     * @param mirror Mirror to apply
     * @param palette Custom palette (-1 for random)
     * @param integrity Structure integrity (0.0-1.0, 1.0 = full structure)
     */
    public void pasteStructure(
            Structure structure,
            Location location,
            boolean includeEntities,
            StructureRotation rotation,
            Mirror mirror,
            int palette,
            float integrity
    ) {
        // Place structure
        structure.place(
                location,
                includeEntities,
                rotation,
                mirror,
                palette,
                integrity,
                RANDOM
        );

        LOGGER.fine("Placed structure at " + location +
                " (rotation: " + rotation + ", mirror: " + mirror + ")");
    }

    /**
     * Place structure with random rotation
     */
    public void pasteStructureRandomRotation(Structure structure, Location location) {
        StructureRotation rotation = StructureRotation.values()[RANDOM.nextInt(4)];
        pasteStructure(structure, location, true, rotation, Mirror.NONE, -1, 1.0f);
    }

    /**
     * Get structure size
     */
    public BlockVector getStructureSize(Structure structure) {
        return structure.getSize();
    }

    /**
     * Save structure to file (for future use)
     */
    public void saveStructure(Structure structure, String fileName) throws IOException {
        File file = new File(structuresDirectory, fileName);

        try {
            structureManager.saveStructure(file, structure);
            LOGGER.info("Saved structure to: " + fileName);
        } catch (Exception e) {
            throw new IOException("Failed to save structure: " + e.getMessage(), e);
        }
    }

    /**
     * Unload structure from cache
     */
    public void unloadStructure(String fileName) {
        loadedStructures.remove(fileName);
    }

    /**
     * Clear all cached structures
     */
    public void clearCache() {
        loadedStructures.clear();
    }

    /**
     * Get structures directory
     */
    public File getStructuresDirectory() {
        return structuresDirectory;
    }

    /**
     * Get number of loaded structures
     */
    public int getCachedStructureCount() {
        return loadedStructures.size();
    }
}