package me.vangoo.infrastructure.items;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;

/**
 * Infrastructure Factory: Creates recipe books
 */
public class RecipeBookFactory {
    private static final String NBT_PATHWAY_KEY = "recipe_pathway";
    private static final String NBT_SEQUENCE_KEY = "recipe_sequence";

    /**
     * Create a recipe book for a specific pathway and sequence
     */
    public ItemStack createRecipeBook(PathwayPotions pathwayPotions, int sequence) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta == null) {
            throw new IllegalStateException("Failed to get BookMeta");
        }

        String pathwayName = pathwayPotions.getPathway().getName();
        String sequenceName = pathwayPotions.getPathway().getSequenceName(sequence);

        ChatColor rankColor;
        if (sequence == 0) {
            rankColor = ChatColor.DARK_PURPLE;
        } else if (sequence >= 1 && sequence <= 3) {
            rankColor = ChatColor.GOLD;
        } else if (sequence >= 4 && sequence <= 6) {
            rankColor = ChatColor.AQUA;
        } else {
            rankColor = ChatColor.GREEN;
        }
        meta.setTitle(ChatColor.GRAY + "Рецепт: " + rankColor + sequenceName + ChatColor.GRAY + " (" + sequence + ")");
        meta.setAuthor(ChatColor.GRAY + "Невідомий Алхімік");
        meta.setGeneration(BookMeta.Generation.TATTERED);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Шлях: " + pathwayName);
        lore.add(ChatColor.GRAY + "Послідовність: " + sequence);
        lore.add(ChatColor.GRAY + "Великий Древній : " + pathwayPotions.getPathway().getGroup().getDisplayName());

        meta.setLore(lore);

        // Create pages
        List<String> pages = createRecipePages(pathwayPotions, sequence, sequenceName);
        meta.setPages(pages);

        book.setItemMeta(meta);

        // Add NBT data
        NBTBuilder builder = new NBTBuilder(book);
        book = builder
                .setString(NBT_PATHWAY_KEY, pathwayName)
                .setInt(NBT_SEQUENCE_KEY, sequence)
                .build();

        return book;
    }

    /**
     * Create pages for the recipe book
     */
    private List<String> createRecipePages(PathwayPotions pathwayPotions,
                                           int sequence,
                                           String sequenceName) {
        List<String> pages = new ArrayList<>();

        // Page 1: Title and warning
        StringBuilder page1 = new StringBuilder();
        page1.append(ChatColor.DARK_PURPLE).append(ChatColor.BOLD)
                .append("Рецепт Зілля\n\n")
                .append(ChatColor.RESET).append(ChatColor.GOLD)
                .append(sequenceName).append("\n")
                .append(ChatColor.GRAY).append("Послідовність ").append(sequence)
                .append("\n\n")
                .append(ChatColor.DARK_RED).append("⚠ УВАГА ⚠\n")
                .append(ChatColor.RESET).append(ChatColor.RED)
                .append("Після прочитання цієї книжки, рецепт буде розблокований.");
        pages.add(page1.toString());

        // Page 2: Ingredients list
        StringBuilder page2 = new StringBuilder();
        page2.append(ChatColor.DARK_PURPLE).append(ChatColor.BOLD)
                .append("Інгредієнти:\n\n")
                .append(ChatColor.RESET);

        ItemStack[] ingredients = pathwayPotions.getIngredients(sequence);
        if (ingredients != null && ingredients.length > 0) {
            for (ItemStack ingredient : ingredients) {
                page2.append(ChatColor.YELLOW).append("• ")
                        .append(ChatColor.WHITE)
                        .append(ingredient.getAmount()).append("x ")
                        .append(formatMaterialName(ingredient.getType()))
                        .append("\n");
            }
        } else {
            page2.append(ChatColor.GRAY).append("Інгредієнти невідомі");
        }
        pages.add(page2.toString());

        // Page 3: Instructions
        StringBuilder page3 = new StringBuilder();
        page3.append(ChatColor.DARK_PURPLE).append(ChatColor.BOLD)
                .append("Інструкція:\n\n")
                .append(ChatColor.RESET).append(ChatColor.GRAY)
                .append("1. Встановіть котел над палаючим душевним багаттям\n")
                .append("2. Наповніть котел водою\n")
                .append("3. Кидайте інгредієнти у котел по черзі\n")
                .append("4. Якщо всі інгредієнти правильні, з'явиться зілля!");
        pages.add(page3.toString());

        return pages;
    }

    /**
     * Format material name for display
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return formatted.toString().trim();
    }

    /**
     * Check if ItemStack is a recipe book
     */
    public boolean isRecipeBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return false;
        }

        NBTBuilder builder = new NBTBuilder(item);
        return builder.getString(item, NBT_PATHWAY_KEY).isPresent() &&
                builder.getInt(item, NBT_SEQUENCE_KEY).isPresent();
    }

    /**
     * Get pathway name from recipe book
     */
    public Optional<String> getPathwayName(ItemStack book) {
        if (!isRecipeBook(book)) {
            return Optional.empty();
        }

        NBTBuilder builder = new NBTBuilder(book);
        return builder.getString(book, NBT_PATHWAY_KEY);
    }

    /**
     * Get sequence from recipe book
     */
    public Optional<Integer> getSequence(ItemStack book) {
        if (!isRecipeBook(book)) {
            return Optional.empty();
        }

        NBTBuilder builder = new NBTBuilder(book);
        return builder.getInt(book, NBT_SEQUENCE_KEY);
    }
}