package me.vangoo.application.services;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.pathways.door.Door;
import me.vangoo.pathways.error.Error;
import me.vangoo.pathways.fool.Fool;
import me.vangoo.pathways.justiciar.Justiciar;
import me.vangoo.pathways.stub.StubPathway;
import me.vangoo.pathways.visionary.Visionary;
import me.vangoo.pathways.whitetower.WhiteTower;
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
        pathways.put("WhiteTower", new WhiteTower(PathwayGroup.GodAlmighty,
                List.of("White Tower", "Omniscient Eye", "Wisdom Angel", "Cognizer", "Prophet", "Mysticism Magister", "Polymath",
                        "Detective", "Student", "Reader")));
        pathways.put("Fool", new Fool(PathwayGroup.LordOfMysteries,
                List.of("Fool", "Lord of Mysteries", "Attendant of Mysteries", "Scholar of Yore", "Bizarro Sorcerer",
                        "Marionettist", "Faceless", "Magician", "Clown", "Seer")));

        registerStub("Sun", PathwayGroup.GodAlmighty, List.of(
                "Sun", "White Angel", "Light Seeker", "Justice Mentor", "Shadowless",
                "Priest of Light", "Notary", "Solar High Priest", "Light Petitioner", "Bard"));
        registerStub("Tyrant", PathwayGroup.GodAlmighty, List.of(
                "Tyrant", "God of Thunder", "Calamity", "Sea King", "Calamity Patron",
                "Ocean Songster", "Wind-Blessed", "Seafarer", "Furious One", "Sailor"));
        registerStub("HangedMan", PathwayGroup.GodAlmighty, List.of(
                "Hanged Man", "Dark Angel", "Profaner Presbyter", "Trinity Templar", "Black Knight",
                "Shepherd", "Rose Bishop", "Shadow Ascetic", "Listener", "Secrets Supplicant"));
        registerStub("Hermit", PathwayGroup.DemonOfKnowledge, List.of(
                "Hermit", "Sage", "Emperor of Knowledge", "Clairvoyant", "Mysticologist",
                "Constellation Magister", "Scroll Professor", "Warlock", "Adjacent Scholar", "Seeker of Mysteries"));
        registerStub("Paragon", PathwayGroup.DemonOfKnowledge, List.of(
                "Paragon", "Illuminator", "Knowledge Mentor", "Arcane Scholar", "Alchemist",
                "Astronomer", "Craftsman", "Appraiser", "Archaeologist", "Scholar"));
        registerStub("BlackEmperor", PathwayGroup.TheAnarchy, List.of(
                "Black Emperor", "Fallen Angel", "Duke of Entropy", "Prince of Abrogation", "Count of the Fallen",
                "Mentor of Disorder", "Baron of Corruption", "Briber", "Barbarian", "Advocate"));
        registerStub("Darkness", PathwayGroup.EternalDarkness, List.of(
                "Darkness", "Knight of Misfortune", "Concealed Servitor", "Bishop of Horror", "Nightwatcher",
                "Spirit Sorcerer", "Soul Assurer", "Nightmare", "Midnight Poet", "Sleepless"));
        registerStub("Death", PathwayGroup.EternalDarkness, List.of(
                "Death", "Pale Emperor", "Consul of Death", "Ferryman", "Undying",
                "Gatekeeper", "Spirit Usher", "Spiritualist", "Gravedigger", "Corpse Collector"));
        registerStub("TwilightGiant", PathwayGroup.EternalDarkness, List.of(
                "Twilight Giant", "Hand of God", "Glory", "Silver Knight", "Demon Hunter",
                "Guardian", "Dawn Paladin", "Weapon Master", "Pugilist", "Warrior"));
        registerStub("Mother", PathwayGroup.GoddessOfOrigin, List.of(
                "Mother", "Nature Walker", "Matriarch of Desolation", "Life Giver", "Ancient Alchemist",
                "Druid", "Biologist", "Harvest Priest", "Physician", "Gardener"));
        registerStub("Moon", PathwayGroup.GoddessOfOrigin, List.of(
                "Moon", "Goddess of Beauty", "Moon Duke", "High Sorcerer", "Shaman King",
                "Crimson Scholar", "Potions Professor", "Vampire", "Beast Tamer", "Apothecary"));
        registerStub("RedPriest", PathwayGroup.CalamityOfDestruction, List.of(
                "Red Priest", "Conqueror", "Weather Warlock", "War Bishop", "Iron-Blooded Knight",
                "Reaper", "Conspirator", "Pyromaniac", "Provoker", "Hunter"));
        registerStub("Demoness", PathwayGroup.CalamityOfDestruction, List.of(
                "Demoness", "Apocalypse", "Catastrophe", "Everlasting", "Despair",
                "Suffering", "Pleasure", "Witch", "Instigator", "Assassin"));
        registerStub("Abyss", PathwayGroup.FatherOfDevils, List.of(
                "Abyss", "Filthy Monarch", "Bloody Archduke", "Prattler", "Demon",
                "Apostle of Desire", "Devil", "Serial Killer", "Wingless Angel", "Criminal"));
        registerStub("Chained", PathwayGroup.FatherOfDevils, List.of(
                "Chained", "Abomination", "Ancient Curse", "Disciple of Silence", "Doll",
                "Ghost", "Zombie", "Werewolf", "Sleepwalker", "Prisoner"));
        registerStub("WheelOfFortune", PathwayGroup.KeyOfLight, List.of(
                "Wheel of Fortune", "Mercury Serpent", "Diviner", "Mage of Misfortune", "Chaos Walker",
                "Victor", "Priest of Misfortune", "Lucky One", "Robot", "Monster"));
    }

    private void registerStub(String name, PathwayGroup group, List<String> sequenceNames) {
        pathways.put(name, new StubPathway(group, name, sequenceNames));
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