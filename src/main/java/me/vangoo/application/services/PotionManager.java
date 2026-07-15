package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.pathways.door.DoorPotions;
import me.vangoo.pathways.error.ErrorPotions;
import me.vangoo.pathways.fool.FoolPotions;
import me.vangoo.pathways.justiciar.JusticiarPotions;
import me.vangoo.pathways.visionary.VisionaryPotions;
import me.vangoo.pathways.whitetower.WhiteTowerPotions;
import me.vangoo.pathways.sun.SunPotions;
import me.vangoo.pathways.tyrant.TyrantPotions;
import me.vangoo.pathways.hangedman.HangedManPotions;
import me.vangoo.pathways.hermit.HermitPotions;
import me.vangoo.pathways.paragon.ParagonPotions;
import me.vangoo.pathways.blackemperor.BlackEmperorPotions;
import me.vangoo.pathways.darkness.DarknessPotions;
import me.vangoo.pathways.death.DeathPotions;
import me.vangoo.pathways.twilightgiant.TwilightGiantPotions;
import me.vangoo.pathways.mother.MotherPotions;
import me.vangoo.pathways.moon.MoonPotions;
import me.vangoo.pathways.redpriest.RedPriestPotions;
import me.vangoo.pathways.demoness.DemonessPotions;
import me.vangoo.pathways.abyss.AbyssPotions;
import me.vangoo.pathways.chained.ChainedPotions;
import me.vangoo.pathways.wheeloffortune.WheelOfFortunePotions;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.PotionItemFactory;
import org.bukkit.inventory.ItemStack;

import me.vangoo.domain.brewing.RecipeDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        potions.add(new SunPotions(
                pathwayManager.getPathway("Sun"),
                customItemService,
                recipeConfig.getOrDefault("Sun", Map.of())));

        potions.add(new TyrantPotions(
                pathwayManager.getPathway("Tyrant"),
                customItemService,
                recipeConfig.getOrDefault("Tyrant", Map.of())));

        potions.add(new HangedManPotions(
                pathwayManager.getPathway("HangedMan"),
                customItemService,
                recipeConfig.getOrDefault("HangedMan", Map.of())));

        potions.add(new HermitPotions(
                pathwayManager.getPathway("Hermit"),
                customItemService,
                recipeConfig.getOrDefault("Hermit", Map.of())));

        potions.add(new ParagonPotions(
                pathwayManager.getPathway("Paragon"),
                customItemService,
                recipeConfig.getOrDefault("Paragon", Map.of())));

        potions.add(new BlackEmperorPotions(
                pathwayManager.getPathway("BlackEmperor"),
                customItemService,
                recipeConfig.getOrDefault("BlackEmperor", Map.of())));

        potions.add(new DarknessPotions(
                pathwayManager.getPathway("Darkness"),
                customItemService,
                recipeConfig.getOrDefault("Darkness", Map.of())));

        potions.add(new DeathPotions(
                pathwayManager.getPathway("Death"),
                customItemService,
                recipeConfig.getOrDefault("Death", Map.of())));

        potions.add(new TwilightGiantPotions(
                pathwayManager.getPathway("TwilightGiant"),
                customItemService,
                recipeConfig.getOrDefault("TwilightGiant", Map.of())));

        potions.add(new MotherPotions(
                pathwayManager.getPathway("Mother"),
                customItemService,
                recipeConfig.getOrDefault("Mother", Map.of())));

        potions.add(new MoonPotions(
                pathwayManager.getPathway("Moon"),
                customItemService,
                recipeConfig.getOrDefault("Moon", Map.of())));

        potions.add(new RedPriestPotions(
                pathwayManager.getPathway("RedPriest"),
                customItemService,
                recipeConfig.getOrDefault("RedPriest", Map.of())));

        potions.add(new DemonessPotions(
                pathwayManager.getPathway("Demoness"),
                customItemService,
                recipeConfig.getOrDefault("Demoness", Map.of())));

        potions.add(new AbyssPotions(
                pathwayManager.getPathway("Abyss"),
                customItemService,
                recipeConfig.getOrDefault("Abyss", Map.of())));

        potions.add(new ChainedPotions(
                pathwayManager.getPathway("Chained"),
                customItemService,
                recipeConfig.getOrDefault("Chained", Map.of())));

        potions.add(new WheelOfFortunePotions(
                pathwayManager.getPathway("WheelOfFortune"),
                customItemService,
                recipeConfig.getOrDefault("WheelOfFortune", Map.of())));
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