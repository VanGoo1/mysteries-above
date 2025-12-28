package me.vangoo.application.services;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.door.Door;
import me.vangoo.domain.pathways.error.Error;
import me.vangoo.domain.pathways.visionary.Visionary;

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
        pathways.put("Door", new Door(PathwayGroup.LordOfMysteries,
                List.of("Door", "Key of Stars", "Planeswalker", "Wanderer", "Secrets Sorcerer", "Traveler", "Scribe",
                        "Astrologer", "Trickmaster", "Apprentice")));
    }

    public Pathway getPathway(String name) {
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