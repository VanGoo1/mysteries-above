package me.vangoo.pathways.stub;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;

/**
 * Зілля шляху-заглушки: колір рідини й колір назви беруться з брендингу,
 * рецептів варіння немає (зілля лише створюються, не варяться).
 */
public class StubPotions extends PathwayPotions {

    public StubPotions(Pathway pathway, Color potionColor, ChatColor nameColor, IItemResolver itemResolver) {
        super(pathway, potionColor, nameColor, List.of(), itemResolver);
        // рецепти не вантажаться — loadRecipes не викликається
    }
}
