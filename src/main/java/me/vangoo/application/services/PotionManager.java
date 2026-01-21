package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.pathways.door.DoorPotions;
import me.vangoo.domain.pathways.error.ErrorPotions;
import me.vangoo.domain.pathways.justiciar.JusticiarPotions;
import me.vangoo.domain.pathways.visionary.VisionaryPotions;
import me.vangoo.domain.pathways.whitetower.WhiteTowerPotions;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.PotionItemFactory;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            CustomItemService customItemService) {
        this.pathwayManager = pathwayManager;
        this.potionItemFactory = potionItemFactory;
        this.potions = new ArrayList<>();
        initializePotions(customItemService);
    }

    private void initializePotions(CustomItemService customItemService) {
        // Initialize pathway potions with custom item service
        potions.add(new ErrorPotions(
                pathwayManager.getPathway("Error"),
                Color.fromRGB(26, 0, 181),
                customItemService
        ));

        potions.add(new VisionaryPotions(
                pathwayManager.getPathway("Visionary"),
                Color.fromRGB(128, 128, 128),
                customItemService
        ));

        potions.add(new DoorPotions(
                pathwayManager.getPathway("Door"),
                Color.fromRGB(0, 0, 115),
                customItemService
        ));

        potions.add(new JusticiarPotions(
                pathwayManager.getPathway("Justiciar"),
                Color.fromRGB(255, 255, 0),
                customItemService
        ));
        potions.add(new WhiteTowerPotions(
                pathwayManager.getPathway("WhiteTower"),
                Color.fromRGB(255, 0, 50),
                customItemService
        ));
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