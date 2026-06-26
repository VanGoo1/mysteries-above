package me.vangoo.domain;

import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PathwayPotions {
    protected HashMap<Integer, ItemStack[]> ingredientsPerSequence;       // основні
    protected HashMap<Integer, ItemStack[]> auxIngredientsPerSequence;    // допоміжні

    private final Pathway pathway;
    private final Color potionColor;
    private final ChatColor nameColor;
    private final List<String> description;
    protected final IItemResolver itemResolver;

    public PathwayPotions(Pathway pathway, Color potionColor, ChatColor nameColor, List<String> description, IItemResolver itemResolver) {
        this.potionColor = potionColor;
        this.pathway = pathway;
        this.nameColor = nameColor;
        this.description = description;
        this.itemResolver = itemResolver;
        this.ingredientsPerSequence = new HashMap<>();
        this.auxIngredientsPerSequence = new HashMap<>();
    }

    /** Наповнює рецепти з конфіга: резолвить id у предмети й розкладає на основні/допоміжні. */
    protected void loadRecipes(Map<Integer, RecipeDefinition> defs) {
        if (defs == null) {
            return;
        }
        for (Map.Entry<Integer, RecipeDefinition> entry : defs.entrySet()) {
            int sequence = entry.getKey();
            List<ItemStack> main = resolveAll(entry.getValue().mainIds());
            List<ItemStack> aux = resolveAll(entry.getValue().auxIds());
            addIngredientsRecipe(sequence, main, aux);
        }
    }

    private List<ItemStack> resolveAll(List<String> ids) {
        List<ItemStack> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            itemResolver.createItemStack(id).ifPresent(out::add);
        }
        return out;
    }

    /** Старий API: усе основне, 0 допоміжних (зберігається для зворотної сумісності). */
    protected void addIngredientsRecipe(int sequence, ItemStack... ingredients) {
        ingredientsPerSequence.put(sequence, ingredients);
    }

    /** Новий API: окремо основні та допоміжні. */
    protected void addIngredientsRecipe(int sequence, List<ItemStack> main, List<ItemStack> aux) {
        ingredientsPerSequence.put(sequence, main.toArray(new ItemStack[0]));
        auxIngredientsPerSequence.put(sequence, aux.toArray(new ItemStack[0]));
    }

    public ItemStack[] getMainIngredients(int sequence) {
        return ingredientsPerSequence.get(sequence);
    }

    public ItemStack[] getAuxiliaryIngredients(int sequence) {
        return auxIngredientsPerSequence.get(sequence);
    }

    /** Об'єднання основних+допоміжних (для відображення). {@code null}, якщо рецепта немає. */
    public ItemStack[] getIngredients(int sequence) {
        ItemStack[] main = ingredientsPerSequence.get(sequence);
        ItemStack[] aux = auxIngredientsPerSequence.get(sequence);
        if (main == null && aux == null) {
            return null;
        }
        List<ItemStack> all = new ArrayList<>();
        if (main != null) all.addAll(Arrays.asList(main));
        if (aux != null) all.addAll(Arrays.asList(aux));
        return all.toArray(new ItemStack[0]);
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
