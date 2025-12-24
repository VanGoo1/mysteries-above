package me.vangoo.infrastructure.items;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.List;

public class PotionItemFactory {

    private static final String NBT_PATHWAY_KEY = "pathway";
    private static final String NBT_SEQUENCE_KEY = "sequence";

    public PotionItemFactory() {

    }

    public ItemStack createSequencePotion(PathwayPotions potions, Sequence sequence) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta==null) throw new IllegalStateException("Failed to get PotionMeta");

        String displayName = potions.getNameColor() + "" + ChatColor.BOLD + "Послідовність " + sequence.level() + " - " + potions.getPathway().getSequenceName(sequence.level());
        meta.setDisplayName(displayName);
        meta.setColor(potions.getPotionColor());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        List<String> lore = createLore(potions.getPathway(), sequence, potions.getDescription());
        meta.setLore(lore);
        potion.setItemMeta(meta);
        NBTBuilder builder = new NBTBuilder(potion);
        potion = builder
                .setString(NBT_PATHWAY_KEY, potions.getPathway().getName())
                .setInt(NBT_SEQUENCE_KEY, sequence.level())
                .build();
        return potion;
    }

    /**
     * Create lore from potion data
     */
    private List<String> createLore(Pathway pathway, Sequence sequence, List<String> description) {
        List<String> lore = new ArrayList<>();

        // Header
        lore.add("§8Шлях: " + pathway.getName());
        lore.add("§8Послідовність: " + sequence.level());
        lore.add("§8" + "─".repeat(30));

        // Description
        lore.addAll(description);

        return lore;
    }

    /**
     * Extract pathway name from potion item
     */
    public String getPathwayName(ItemStack potion) {
        NBTBuilder builder = new NBTBuilder(potion);
        return builder.getString(potion, NBT_PATHWAY_KEY)
                .orElseThrow(() -> new IllegalArgumentException("Not a pathway potion"));
    }

    /**
     * Extract sequence from potion item
     */
    public Sequence getSequence(ItemStack potion) {
        NBTBuilder builder = new NBTBuilder(potion);
        int value = builder.getInt(potion, NBT_SEQUENCE_KEY)
                .orElseThrow(() -> new IllegalArgumentException("Not a pathway potion"));
        return Sequence.of(value);
    }

    /**
     * Check if item is a pathway potion
     */
    public boolean isPathwayPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }

        NBTBuilder builder = new NBTBuilder(item);
        return builder.getString(item, NBT_PATHWAY_KEY).isPresent() &&
                builder.getInt(item, NBT_SEQUENCE_KEY).isPresent();
    }
}
