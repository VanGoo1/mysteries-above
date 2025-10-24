package me.vangoo.managers;

import me.vangoo.domain.Pathway;
import me.vangoo.domain.PathwayGroup;
import me.vangoo.implementation.ErrorPathway.Error;
import me.vangoo.implementation.VisionaryPathway.Visionary;

import java.util.Collection;
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
        pathways.put("Visionary", new Visionary(PathwayGroup.GodAlmighty,
                List.of("Visionary", "Author", "Discerner", "Dream Weaver", "Manipulator", "Dreamwalker", "Hypnotist",
                        "Psychiatrist", "Telepathist", "Spectator")));
    }

    public Pathway getPathway(String name) {
        // Краще зробити пошук нечутливим до регістру для зручності гравців
        return pathways.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public Collection<String> getAllPathwayNames() {
        return pathways.keySet();
    }
}