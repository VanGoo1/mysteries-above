package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.Structure;
import me.vangoo.infrastructure.structures.NBTStructureLoader;
import org.bukkit.Location;

import java.util.*;
import java.util.logging.Logger;

/**
 * Application Service: Manages structure definitions and placement
 */
public class StructureService {
    private static final Logger LOGGER = Logger.getLogger(StructureService.class.getName());

    private final Map<String, Structure> structures;
    private final NBTStructureLoader nbtLoader;

    public StructureService(NBTStructureLoader nbtLoader) {
        this.structures = new HashMap<>();
        this.nbtLoader = nbtLoader;
    }

    /**
     * Register a structure
     */
    public void registerStructure(Structure structure) {
        structures.put(structure.id(), structure);
        LOGGER.info("Registered structure: " + structure.id());
    }

    /**
     * Register multiple structures
     */
    public void registerAll(Map<String, Structure> structures) {
        structures.forEach((id, structure) -> registerStructure(structure));
    }

    /**
     * Get structure by ID
     */
    public Optional<Structure> getStructure(String id) {
        return Optional.ofNullable(structures.get(id));
    }

    /**
     * Get all structures
     */
    public Collection<Structure> getAllStructures() {
        return Collections.unmodifiableCollection(structures.values());
    }

    /**
     * Place structure at location
     */
    public boolean placeStructure(String structureId, Location location) {
        Structure structure = structures.get(structureId);
        if (structure == null) {
            LOGGER.warning("Structure not found: " + structureId);
            return false;
        }

        try {
            org.bukkit.structure.Structure bukkitStructure = nbtLoader.loadStructure(structure.nbtFileName());
            nbtLoader.pasteStructure(bukkitStructure, location);
            LOGGER.info("Placed structure " + structureId + " at " + location);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to place structure " + structureId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Place structure with random rotation
     */
    public boolean placeStructureRandomRotation(String structureId, Location location) {
        Structure structure = structures.get(structureId);
        if (structure == null) {
            LOGGER.warning("Structure not found: " + structureId);
            return false;
        }

        try {
            org.bukkit.structure.Structure bukkitStructure = nbtLoader.loadStructure(structure.nbtFileName());
            nbtLoader.pasteStructureRandomRotation(bukkitStructure, location);
            LOGGER.info("Placed structure " + structureId + " with random rotation at " + location);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to place structure " + structureId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStructures", structures.size());
        stats.put("structuresWithLoot", structures.values().stream()
                .filter(Structure::hasLootTables)
                .count());
        stats.put("cachedStructures", nbtLoader.getCachedStructureCount());
        return stats;
    }
}