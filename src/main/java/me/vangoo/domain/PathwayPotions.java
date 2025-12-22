package me.vangoo.domain;

import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class PathwayPotions {
    protected HashMap<Integer, ItemStack[]> mainIngredients;
    protected HashMap<Integer, ItemStack[]> supplementaryIngredients;
    private Pathway pathway;
    private Color potionColor;
    private ChatColor nameColor;
    private List<String> loreDescriptionSection;

    public PathwayPotions(Pathway pathway, Color potionColor, ChatColor nameColor, List<String> loreDescriptionSection) {
        this.potionColor = potionColor;
        this.pathway = pathway;
        this.nameColor = nameColor;
        this.loreDescriptionSection = loreDescriptionSection;
        mainIngredients = new HashMap<>();
        supplementaryIngredients = new HashMap<>();
    }

    public ItemStack returnPotionForSequence(int sequence) {
        return generateItemPotion(
                nameColor,
                pathway.getSequenceName(sequence),
                pathway.getName(),
                sequence,
                potionColor,
                loreDescriptionSection
        );
    }

    private static ItemStack generateItemPotion(ChatColor nameColor, String sequenceName, String pathwayName, int sequence, Color potionColor, List<String> description) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setDisplayName(nameColor + "" + ChatColor.BOLD + "Послідовність " + sequence + " - " + sequenceName);
        meta.setColor(potionColor);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(Arrays.asList(
                "§8Шлях: " + pathwayName,
                "§8Послідовність: " + sequence
        ));
        meta.setLore(description);

        meta.getPersistentDataContainer().set(
                new NamespacedKey("beyonder", "pathway"),
                PersistentDataType.STRING,
                pathwayName
        );

        meta.getPersistentDataContainer().set(
                new NamespacedKey("beyonder", "sequence"),
                PersistentDataType.INTEGER,
                sequence
        );

        potion.setItemMeta(meta);
        return potion;
    }

    protected void addMainIngredientsRecipe(int sequence, ItemStack... ingredients) {
        mainIngredients.put(sequence, ingredients);
    }

    protected void addMainSupplementaryRecipe(int sequence, ItemStack... ingredients) {
        supplementaryIngredients.put(sequence, ingredients);
    }

    public Pathway getPathway() {
        return pathway;
    }
}
