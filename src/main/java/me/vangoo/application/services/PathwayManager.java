package me.vangoo.application.services;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.door.Door;
import me.vangoo.domain.pathways.error.Error;
import me.vangoo.domain.pathways.justiciar.Justiciar;
import me.vangoo.domain.pathways.visionary.Visionary;
import me.vangoo.domain.valueobjects.AbilityIdentity;

import java.util.*;

public class PathwayManager {
    private final Map<String, Pathway> pathways;

    public PathwayManager() {
        pathways = new HashMap<>();
        initializePathways();
    }

    private void initializePathways() {
        pathways.put("Error", new Error(PathwayGroup.LordOfMysteries,
                List.of("Error", "Worm of Time", "Fate Stealer", "Mentor of Deceit", "Parasite", "Dream Stealer",
                        "Prometheus", "Cryptologist", "Swindler", "Marauder")));
        pathways.put("Visionary", new Visionary(PathwayGroup.GodAlmighty,
                List.of("Visionary", "Author", "Discerner", "Dream Weaver", "Manipulator", "Dreamwalker", "Hypnotist",
                        "Psychiatrist", "Telepathist", "Spectator")));
        pathways.put("Door", new Door(PathwayGroup.LordOfMysteries,
                List.of("Door", "Key of Stars", "Planeswalker", "Wanderer", "Secrets Sorcerer", "Traveler", "Scribe",
                        "Astrologer", "Trickmaster", "Apprentice")));
        pathways.put("Justiciar", new Justiciar(PathwayGroup.TheAnarchy,
                List.of("Justiciar", "Hand of Order", "Balancer", "Chaos Hunter", "Imperative Mage", "Paladin", "Judge",
                        "Interrogator", "Sheriff", "Arbiter")));
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

    public Ability findAbilityInAllPathways(AbilityIdentity abilityIdentity) {
        Collection<Pathway> allPathways = pathways.values();

        for (Pathway pathway : allPathways) {
            for (int i = 0; i <= 9; i++) {
                List<Ability> abilities = pathway.GetAbilitiesForSequence(i);
                for (Ability ability : abilities) {
                    if (Objects.equals(ability.getIdentity().id(), abilityIdentity.id())) {
                        return ability;
                    }
                }
            }
        }
        return null;
    }
}