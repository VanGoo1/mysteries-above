package me.vangoo.domain;

import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;

public abstract class PathwayPotions {
    protected HashMap<Integer, ItemStack[]> ingredientsPerSequence;

    private final Pathway pathway;
    private final Color potionColor;
    private final ChatColor nameColor;
    private final List<String> description;

    public PathwayPotions(Pathway pathway, Color potionColor, ChatColor nameColor, List<String> description) {
        this.potionColor = potionColor;
        this.pathway = pathway;
        this.nameColor = nameColor;
        this.description = description;
        ingredientsPerSequence = new HashMap<>();
    }

    protected void addIngredientsRecipe(int sequence, ItemStack... ingredients) {
        ingredientsPerSequence.put(sequence, ingredients);
    }

    public Pathway getPathway() {
        return pathway;
    }

    public Color getPotionColor() {
        return potionColor;
    }

    public ChatColor getNameColor() {
        return nameColor;
    }

    public List<String> getDescription() {
        return description;
    }
}