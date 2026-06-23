package me.vangoo.domain.pathways.fool;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.PathwayPotions;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;

/**
 * Potion recipes for the Fool pathway.
 *
 * LOTM-canonical ingredients adapted for Minecraft custom items.
 * These custom items must be registered in the item configuration.
 */
public class FoolPotions extends PathwayPotions {

    public FoolPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver) {
        super(pathway, potionColor, ChatColor.DARK_PURPLE, List.of(), itemResolver);

        // Sequence 9: Seer — нічне бачення та містична інтуїція
        var nighthawkEyeball = itemResolver.createItemStack("nighthawk_eyeball");
        var stellarAquaCrystal = itemResolver.createItemStack("stellar_aqua_crystal");
        var dragonSavagelandPollen = itemResolver.createItemStack("dragon_savageland_pollen");
        var crimsonStar = itemResolver.createItemStack("crimson_star");

        // Sequence 8: Clown — фізичне вдосконалення та маскування
        var jokerBloodEssence = itemResolver.createItemStack("joker_blood_essence");
        var marbledIvoryShard = itemResolver.createItemStack("marbled_ivory_shard");
        var chameleonSlime = itemResolver.createItemStack("chameleon_slime");

        // Sequence 7: Magician — маніпуляція реальністю та ілюзії
        var flamingoCrystal = itemResolver.createItemStack("flamingo_crystal");
        var meteoriteFragment = itemResolver.createItemStack("meteorite_fragment");
        var phantomInk = itemResolver.createItemStack("phantom_ink");

        // Sequence 6: Faceless — трансформація та ідентичність
        var shapeshifterGland = itemResolver.createItemStack("shapeshifter_gland");
        var depthsShadowEssence = itemResolver.createItemStack("depths_shadow_essence");
        var mirrorSilverDust = itemResolver.createItemStack("mirror_silver_dust");
        var soulWax = itemResolver.createItemStack("soul_wax");

        // Sequence 5: Marionettist — контроль та маріонетки
        var spiritThreadSpool = itemResolver.createItemStack("spirit_thread_spool");
        var puppeteerHeartstring = itemResolver.createItemStack("puppeteer_heartstring");
        var wraithholdEssence = itemResolver.createItemStack("wraithhold_essence");

        if (nighthawkEyeball.isPresent() && stellarAquaCrystal.isPresent()
                && dragonSavagelandPollen.isPresent() && crimsonStar.isPresent()
                && jokerBloodEssence.isPresent() && marbledIvoryShard.isPresent()
                && chameleonSlime.isPresent()
                && flamingoCrystal.isPresent() && meteoriteFragment.isPresent()
                && phantomInk.isPresent()
                && shapeshifterGland.isPresent() && depthsShadowEssence.isPresent()
                && mirrorSilverDust.isPresent() && soulWax.isPresent()
                && spiritThreadSpool.isPresent() && puppeteerHeartstring.isPresent()
                && wraithholdEssence.isPresent()) {

            addIngredientsRecipe(9,
                    nighthawkEyeball.get(), stellarAquaCrystal.get(),
                    dragonSavagelandPollen.get(), crimsonStar.get());

            addIngredientsRecipe(8,
                    jokerBloodEssence.get(), marbledIvoryShard.get(),
                    chameleonSlime.get());

            addIngredientsRecipe(7,
                    flamingoCrystal.get(), meteoriteFragment.get(),
                    phantomInk.get());

            addIngredientsRecipe(6,
                    shapeshifterGland.get(), depthsShadowEssence.get(),
                    mirrorSilverDust.get(), soulWax.get());

            addIngredientsRecipe(5,
                    spiritThreadSpool.get(), puppeteerHeartstring.get(),
                    wraithholdEssence.get());
        }
    }
}
