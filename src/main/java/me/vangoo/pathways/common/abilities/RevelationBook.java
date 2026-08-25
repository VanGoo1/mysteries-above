package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.rituals.IngredientHint;
import me.vangoo.domain.rituals.IngredientSourceIndex;
import me.vangoo.infrastructure.items.BookTitles;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Книга Ритуалу одкровення: НАТЯК, де шукати інгредієнти наступної Послідовності.
 * Свідомо не рецепт — жодного NBT, тож {@code RecipeBookFactory.isRecipeBook} її не впізнає
 * і прочитання нічого не розблоковує (див. .claude/rules/ritual-magic.md).
 * Точних імен мобів і біомів не називає: пише сімейство місцевості.
 */
final class RevelationBook {

    private static final int HINTS_PER_PAGE = 2;

    private RevelationBook() {
    }

    static ItemStack create(String pathwayName, String sequenceName, int sequence,
                            List<IngredientHint> hints) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;

        // Коротке "Слід:" замість "Одкровення:" — інакше довгі назви Послідовностей
        // (Matriarch of Desolation) не влазять у ліміт заголовка й ріжуться.
        meta.setTitle(BookTitles.fit(ChatColor.GRAY + "Слід: " + ChatColor.LIGHT_PURPLE + sequenceName));
        meta.setAuthor(ChatColor.GRAY + "Голос за Завісою");
        meta.setGeneration(BookMeta.Generation.TATTERED);
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Шлях: " + pathwayName,
                ChatColor.GRAY + "Послідовність: " + sequence,
                ChatColor.DARK_GRAY + "Не рецепт — лише сліди."
        ));
        meta.setPages(pages(sequenceName, hints));
        book.setItemMeta(meta);
        return book;
    }

    private static List<String> pages(String sequenceName, List<IngredientHint> hints) {
        List<String> pages = new ArrayList<>();
        pages.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Одкровення\n\n"
                + ChatColor.RESET + ChatColor.GOLD + sequenceName + "\n\n"
                + ChatColor.RESET + ChatColor.DARK_GRAY
                + "Сутності не дають речей. Вони дають напрямок.");

        StringBuilder page = new StringBuilder();
        int onPage = 0;
        for (IngredientHint hint : hints) {
            page.append(ChatColor.DARK_PURPLE).append("✦ ")
                    .append(ChatColor.RESET).append(ChatColor.BLACK)
                    .append(ChatColor.stripColor(hint.displayName())).append("\n")
                    .append(ChatColor.DARK_GRAY).append(trace(hint.source())).append("\n\n");
            if (++onPage == HINTS_PER_PAGE) {
                pages.add(page.toString());
                page = new StringBuilder();
                onPage = 0;
            }
        }
        if (onPage > 0) pages.add(page.toString());
        return pages;
    }

    private static String trace(IngredientSourceIndex.Source source) {
        if (source == null || source.biomes().isEmpty()) {
            return "Сліди губляться. Хіба що в чиїхось старих скринях.";
        }
        String where = String.join(", ", families(source.biomes()));
        return source.kind() == IngredientSourceIndex.Kind.CREATURE
                ? "Носить його жива тварь. Шукай " + where + "."
                : "Росте серед дикої зелені. Шукай " + where + ".";
    }

    /** Сімейство місцевості замість точного біома — натяк, а не карта. */
    private static Set<String> families(List<String> biomes) {
        Set<String> out = new LinkedHashSet<>();
        for (String biome : biomes) {
            out.add(family(biome));
        }
        return out;
    }

    private static String family(String biome) {
        if (biome.contains("NETHER") || biome.contains("CRIMSON")
                || biome.contains("WARPED") || biome.contains("BASALT")
                || biome.contains("SOUL_SAND")) return "у Незері";
        if (biome.contains("CAVES") || biome.contains("DEEP_DARK")) return "у глибоких печерах";
        if (biome.contains("OCEAN")) return "у морях";
        if (biome.contains("SHORE") || biome.contains("BEACH")
                || biome.contains("RIVER")) return "на берегах";
        if (biome.contains("SWAMP")) return "у болотах";
        if (biome.contains("JUNGLE")) return "у джунглях";
        if (biome.contains("DESERT") || biome.contains("BADLANDS")) return "у пустелях";
        if (biome.contains("SAVANNA")) return "у саванах";
        if (biome.contains("PEAKS") || biome.contains("SLOPES")
                || biome.contains("WINDSWEPT") || biome.contains("GROVE")) return "у горах";
        if (biome.contains("MEADOW")) return "на луках";
        if (biome.contains("DARK_FOREST")) return "у темних лісах";
        if (biome.contains("FOREST") || biome.contains("TAIGA")) return "у лісах";
        if (biome.contains("PLAINS")) return "на рівнинах";
        return "у диких землях";
    }
}
