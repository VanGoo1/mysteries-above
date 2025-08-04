package me.vangoo.pathways;

import me.vangoo.implementation.ErrorPathway.Error;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathwayManager {
    private final Map<String, Pathway> pathways;

    public PathwayManager() {
        pathways = new HashMap<>();
        initializePathways();
    }

    private void initializePathways() {
        pathways.put("Error", new Error(PathwayGroup.LordOfMysteries,
                List.of("Error (Bug)", "Worm of Time", "Fate Stealer", "Mentor of Deceit", "Parasite", "Dream Stealer",
                        "Prometheus", "Cryptologist", "Swindler", "Marauder")));
    }

    public Pathway getPathway(String name) {
        return pathways.get(name);
    }
}
