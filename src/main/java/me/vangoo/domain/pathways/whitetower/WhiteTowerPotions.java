package me.vangoo.domain.pathways.whitetower;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;

public class WhiteTowerPotions extends PathwayPotions {
    public WhiteTowerPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.RED, List.of(), itemResolver);
        var dimensionalWandererEye = itemResolver.createItemStack("dimensional_wanderer_eye");
        var everShiftingLotus = itemResolver.createItemStack("ever_shifting_lotus");
        var spiritEaterPouch = itemResolver.createItemStack("spirit_eater_pouch");
        var deepSeaMarlinBlood = itemResolver.createItemStack("deep_sea_marlin_blood");
        var stringGrassPowder = itemResolver.createItemStack("string_grass_powder");
        var redChestnutFlower = itemResolver.createItemStack("red_chestnut_flower");
        var meteoriteCrystal = itemResolver.createItemStack("meteorite_crystal");
        var lavosSquidBlood = itemResolver.createItemStack("lavos_squid_blood");
        var starfacedStone = itemResolver.createItemStack("starfaced_stone");
        var asmannCompleteBrain = itemResolver.createItemStack("asmann_complete_brain");
        var cursedWraithArtifact = itemResolver.createItemStack("cursed_wraith_artifact");
        var imbuedInkSac = itemResolver.createItemStack("imbued_ink_sac");
        var voidDrifterHeart = itemResolver.createItemStack("void_drifter_heart");
        var astralMistEssence = itemResolver.createItemStack("astral_mist_essence");
        var wayfinderTreeRoot = itemResolver.createItemStack("wayfinder_tree_root");

        if (dimensionalWandererEye.isPresent()) {

            addIngredientsRecipe(9);
            addIngredientsRecipe(8);
            addIngredientsRecipe(7);
            addIngredientsRecipe(6);
            addIngredientsRecipe(5);
        }
    }
}
