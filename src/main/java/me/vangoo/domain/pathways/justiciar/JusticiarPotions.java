package me.vangoo.domain.pathways.justiciar;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public class JusticiarPotions extends PathwayPotions {

    public JusticiarPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        // Колір зілля для Justiciar зазвичай золотистий або мідний, але тут залишаємо переданий
        super(pathway, potionColor, ChatColor.GOLD, List.of(), itemResolver);

        registerSeq9(itemResolver);
        registerSeq8(itemResolver);
        registerSeq7(itemResolver);
        registerSeq6(itemResolver);
        registerSeq5(itemResolver);
    }

    private void registerSeq9(IItemResolver resolver) {
        var heart = resolver.createItemStack("heart_of_unquestioned_command");
        var bone = resolver.createItemStack("bone_of_the_unyielding_frame");
        var eye = resolver.createItemStack("eye_of_judicial_insight");

        if (heart.isPresent() && bone.isPresent() && eye.isPresent()) {
            addIngredientsRecipe(9, heart.get(), bone.get(), eye.get());
        }
    }

    private void registerSeq8(IItemResolver resolver) {
        var demonEyes = resolver.createItemStack("pair_of_terror_demon_worms_eyes");
        var bearPalm = resolver.createItemStack("silver_war_bears_right_palm");
        var recognitionEye = resolver.createItemStack("eye_of_perfect_recognition");
        var intuitionThread = resolver.createItemStack("thread_of_supernatural_intuition");

        if (demonEyes.isPresent() && bearPalm.isPresent() &&
                recognitionEye.isPresent() && intuitionThread.isPresent()) {

            addIngredientsRecipe(8, demonEyes.get(), bearPalm.get(), recognitionEye.get(), intuitionThread.get());
        }
    }

    private void registerSeq7(IItemResolver resolver) {
        var snakeHorn = resolver.createItemStack("horn_of_a_flash_patterned_black_snake");
        var lakeDust = resolver.createItemStack("dust_of_lake_spirit");
        var sealAshes = resolver.createItemStack("ashes_of_a_broken_oath_seal");

        if (snakeHorn.isPresent() && lakeDust.isPresent() && sealAshes.isPresent()) {

            addIngredientsRecipe(7, snakeHorn.get(), lakeDust.get(), sealAshes.get());
        }
    }

    private void registerSeq6(IItemResolver resolver) {
        var verdictHeart = resolver.createItemStack("heart_of_the_silent_verdict");
        var domainFragment = resolver.createItemStack("fragment_of_a_sealed_domain");
        var decreeAshes = resolver.createItemStack("ashes_of_a_forbidden_decree");
        var fearChains = resolver.createItemStack("chains_of_collective_fear");

        if (verdictHeart.isPresent() && domainFragment.isPresent() &&
                decreeAshes.isPresent() && fearChains.isPresent()) {

            addIngredientsRecipe(6, verdictHeart.get(), domainFragment.get(), decreeAshes.get(), fearChains.get());
        }
    }

    private void registerSeq5(IItemResolver resolver) {
        var authorityCore = resolver.createItemStack("core_of_retributive_authority");
        var oathBrand = resolver.createItemStack("brand_of_the_unforgiving_oath");
        var executionAshes = resolver.createItemStack("ashes_of_a_public_execution");
        var guiltyShackles = resolver.createItemStack("shackles_of_the_guilty");

        if (authorityCore.isPresent() && oathBrand.isPresent() &&
                executionAshes.isPresent() && guiltyShackles.isPresent()) {
            addIngredientsRecipe(5, authorityCore.get(), oathBrand.get(), executionAshes.get(), guiltyShackles.get());
        }
    }
}