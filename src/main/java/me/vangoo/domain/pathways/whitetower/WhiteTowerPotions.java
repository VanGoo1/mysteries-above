package me.vangoo.domain.pathways.whitetower;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

public class WhiteTowerPotions extends PathwayPotions {

    public WhiteTowerPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.AQUA, java.util.List.of(), itemResolver);

        registerSeq9(itemResolver);
        registerSeq8(itemResolver);
        registerSeq7(itemResolver);
        registerSeq6(itemResolver);
        registerSeq5(itemResolver);
    }

    /* ============================
       SEQUENCE 9 — WHITE TOWER
       ============================ */
    private void registerSeq9(IItemResolver resolver) {
        var pituitary = resolver.createItemStack("manticore_bird_pituitary_gland");
        var blood = resolver.createItemStack("light_antelope_blood");
        var crystalPowder = resolver.createItemStack("rock_crystal_powder");
        var peppermint = resolver.createItemStack("peppermint_extract");

        if (pituitary.isPresent() && blood.isPresent()
                && crystalPowder.isPresent() && peppermint.isPresent()) {

            addIngredientsRecipe(
                    9,
                    pituitary.get(),
                    blood.get(),
                    crystalPowder.get(),
                    peppermint.get()
            );
        }
    }

    /* ============================
       SEQUENCE 8 — WHITE TOWER
       ============================ */
    private void registerSeq8(IItemResolver resolver) {
        var brain = resolver.createItemStack("crystallized_cave_monkey_brain");
        var petals = resolver.createItemStack("memory_flower_petals");
        var sap = resolver.createItemStack("tree_of_wisdom_sap");
        var amber = resolver.createItemStack("yellow_amber_powder");

        if (brain.isPresent() && petals.isPresent()
                && sap.isPresent() && amber.isPresent()) {

            addIngredientsRecipe(
                    8,
                    brain.get(),
                    petals.get(),
                    sap.get(),
                    amber.get()
            );
        }
    }

    /* ============================
       SEQUENCE 7 — WHITE TOWER
       ============================ */
    private void registerSeq7(IItemResolver resolver) {
        var eye = resolver.createItemStack("phantom_python_vertical_eye");
        var heart = resolver.createItemStack("steel_toothed_wolf_heart");
        var mica = resolver.createItemStack("mica_powder");

        if (eye.isPresent() && heart.isPresent() && mica.isPresent()) {
            addIngredientsRecipe(
                    7,
                    eye.get(),
                    heart.get(),
                    mica.get()
            );
        }
    }

    /* ============================
       SEQUENCE 6 — WHITE TOWER
       ============================ */
    private void registerSeq6(IItemResolver resolver) {
        var heart = resolver.createItemStack("prismatic_chameleon_heart");
        var residue = resolver.createItemStack("liquid_silver_phantom_residue");
        var spores = resolver.createItemStack("mimicry_moss_spores");
        var mask = resolver.createItemStack("devastated_blank_mask");

        if (heart.isPresent() && residue.isPresent()
                && spores.isPresent() && mask.isPresent()) {

            addIngredientsRecipe(
                    6,
                    heart.get(),
                    residue.get(),
                    spores.get(),
                    mask.get()
            );
        }
    }

    /* ============================
       SEQUENCE 5 — WHITE TOWER
       ============================ */
    private void registerSeq5(IItemResolver resolver) {
        var eye = resolver.createItemStack("void_beholder_petrified_eye");
        var nebula = resolver.createItemStack("condensed_astral_nebula");
        var runePowder = resolver.createItemStack("ancient_rune_powder");
        var tear = resolver.createItemStack("pure_spirituality_tear");

        if (eye.isPresent() && nebula.isPresent()
                && runePowder.isPresent() && tear.isPresent()) {

            addIngredientsRecipe(
                    5,
                    eye.get(),
                    nebula.get(),
                    runePowder.get(),
                    tear.get()
            );
        }
    }
}
