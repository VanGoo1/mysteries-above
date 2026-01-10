package me.vangoo.domain.pathways.visionary;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import java.util.List;

public class VisionaryPotions extends PathwayPotions {
    public VisionaryPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.GRAY, List.of(), itemResolver);
        var manhalFishEyeball = itemResolver.createItemStack("manhal_fish_eyeball");
        var goatHornedBlackfishBlood = itemResolver.createItemStack("goat_horned_blackfish_blood");
        var autumnCrocusEssence = itemResolver.createItemStack("autumn_crocus_essence");
        var elfFlowerPetals = itemResolver.createItemStack("elf_flower_petals");
        var crystallineMoonOrchid = itemResolver.createItemStack("crystalline_moon_orchid");
        var thoughtEssence = itemResolver.createItemStack("thought_essence");
        var iridescentPearlDust = itemResolver.createItemStack("iridescent_pearl_dust");
        var treeOfEldersFruit = itemResolver.createItemStack("tree_of_elders_fruit");
        var mirrorDragonEyes = itemResolver.createItemStack("mirror_dragon_eyes");
        var mirrorDragonBlood = itemResolver.createItemStack("mirror_dragon_blood");
        var blackHuntingLizardSpinalFluid = itemResolver.createItemStack("black_hunting_lizard_spinal_fluid");
        var illusoryChimeTreeFruit = itemResolver.createItemStack("illusory_chime_tree_fruit");
        var mindCrystalPowder = itemResolver.createItemStack("mind_crystal_powder");
        var splitPersonalityEssence = itemResolver.createItemStack("split_personality_essence");
        var dreamCatcherHeart = itemResolver.createItemStack("dream_catcher_heart");
        var mindIllusionCrystal = itemResolver.createItemStack("mind_illusion_crystal");
        var adultMindDragonBlood = itemResolver.createItemStack("adult_mind_dragon_blood");

        if (manhalFishEyeball.isPresent() && goatHornedBlackfishBlood.isPresent() &&
                autumnCrocusEssence.isPresent() && elfFlowerPetals.isPresent() && crystallineMoonOrchid.isPresent()
                && thoughtEssence.isPresent() && iridescentPearlDust.isPresent()
                && treeOfEldersFruit.isPresent() && mirrorDragonEyes.isPresent() && mirrorDragonBlood.isPresent()
                && blackHuntingLizardSpinalFluid.isPresent() && illusoryChimeTreeFruit.isPresent() && mindCrystalPowder.isPresent()
                && splitPersonalityEssence.isPresent() && dreamCatcherHeart.isPresent() && mindIllusionCrystal.isPresent()
                && adultMindDragonBlood.isPresent()) {

            addIngredientsRecipe(9, manhalFishEyeball.get(), goatHornedBlackfishBlood.get(), autumnCrocusEssence.get(), elfFlowerPetals.get());
            addIngredientsRecipe(8, crystallineMoonOrchid.get(), thoughtEssence.get(), iridescentPearlDust.get());
            addIngredientsRecipe(7, treeOfEldersFruit.get(), mirrorDragonEyes.get(), mirrorDragonBlood.get());
            addIngredientsRecipe(6, blackHuntingLizardSpinalFluid.get(), illusoryChimeTreeFruit.get(), mindCrystalPowder.get(), splitPersonalityEssence.get());
            addIngredientsRecipe(5, dreamCatcherHeart.get(), mindIllusionCrystal.get(), adultMindDragonBlood.get());
        }
    }
}
