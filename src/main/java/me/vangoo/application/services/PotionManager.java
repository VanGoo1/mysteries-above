package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.pathways.door.DoorPotions;
import me.vangoo.pathways.error.ErrorPotions;
import me.vangoo.pathways.fool.FoolPotions;
import me.vangoo.pathways.justiciar.JusticiarPotions;
import me.vangoo.pathways.stub.StubPotions;
import me.vangoo.pathways.visionary.VisionaryPotions;
import me.vangoo.pathways.whitetower.WhiteTowerPotions;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.PotionItemFactory;
import org.bukkit.inventory.ItemStack;

import me.vangoo.domain.brewing.RecipeDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Application Service: Manages pathway potions
 * Updated to support custom items in recipes
 */
public class PotionManager {
    private final List<PathwayPotions> potions;
    private final PathwayManager pathwayManager;
    private final PotionItemFactory potionItemFactory;

    public PotionManager(
            PathwayManager pathwayManager,
            PotionItemFactory potionItemFactory,
            CustomItemService customItemService,
            Map<String, Map<Integer, RecipeDefinition>> recipeConfig) {
        this.pathwayManager = pathwayManager;
        this.potionItemFactory = potionItemFactory;
        this.potions = new ArrayList<>();
        initializePotions(customItemService, recipeConfig);
    }

    private void initializePotions(CustomItemService customItemService,
                                   Map<String, Map<Integer, RecipeDefinition>> recipeConfig) {
        potions.add(new ErrorPotions(
                pathwayManager.getPathway("Error"),
                customItemService,
                recipeConfig.getOrDefault("Error", Map.of())
        ));

        potions.add(new VisionaryPotions(
                pathwayManager.getPathway("Visionary"),
                customItemService,
                recipeConfig.getOrDefault("Visionary", Map.of())
        ));

        potions.add(new DoorPotions(
                pathwayManager.getPathway("Door"),
                customItemService,
                recipeConfig.getOrDefault("Door", Map.of())
        ));

        potions.add(new JusticiarPotions(
                pathwayManager.getPathway("Justiciar"),
                customItemService,
                recipeConfig.getOrDefault("Justiciar", Map.of())
        ));

        potions.add(new WhiteTowerPotions(
                pathwayManager.getPathway("WhiteTower"),
                customItemService,
                recipeConfig.getOrDefault("WhiteTower", Map.of())
        ));

        potions.add(new FoolPotions(
                pathwayManager.getPathway("Fool"),
                customItemService,
                recipeConfig.getOrDefault("Fool", Map.of())
        ));

        Set<String> stubs = Set.of(
                "Sun", "Tyrant", "HangedMan", "Hermit", "Paragon", "BlackEmperor",
                "Darkness", "Death", "TwilightGiant", "Mother", "Moon",
                "RedPriest", "Demoness", "Abyss", "Chained", "WheelOfFortune");
        for (String name : stubs) {
            potions.add(new StubPotions(
                    pathwayManager.getPathway(name),
                    customItemService));
        }
    }

    public Optional<PathwayPotions> getPotionsPathway(String pathwayName) {
        return potions.stream()
                .filter(potion -> potion.getPathway().getName().equalsIgnoreCase(pathwayName))
                .findFirst();
    }

    /**
     * Create potion ItemStack for a pathway and sequence
     */
    public ItemStack createPotionItem(String pathwayName, Sequence sequence) {
        PathwayPotions pathwayPotions = getPotionsPathway(pathwayName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pathway: " + pathwayName));

        return potionItemFactory.createSequencePotion(pathwayPotions, sequence);
    }

    /**
     * Get pathway name from potion item
     */
    public Optional<String> getPathwayFromItem(ItemStack item) {
        if (!potionItemFactory.isPathwayPotion(item)) {
            return Optional.empty();
        }

        try {
            return Optional.of(potionItemFactory.getPathwayName(item));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get sequence from potion item
     */
    public Optional<Integer> getSequenceFromItem(ItemStack item) {
        if (!potionItemFactory.isPathwayPotion(item)) {
            return Optional.empty();
        }

        try {
            return Optional.of(potionItemFactory.getSequence(item).level());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Check if item is a pathway potion
     */
    public boolean isPathwayPotion(ItemStack item) {
        return potionItemFactory.isPathwayPotion(item);
    }

    public List<PathwayPotions> getPotions() {
        return List.copyOf(potions);
    }
}