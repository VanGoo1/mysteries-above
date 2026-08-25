package me.vangoo.infrastructure.items;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookTitlesTest {

    @Test
    void shortTitleKeepsItsColors() {
        String title = ChatColor.GRAY + "Рецепт: " + ChatColor.AQUA + "Seer";
        assertEquals(title, BookTitles.fit(title));
    }

    /** Саме цей заголовок валив сервер: setTitle його відхиляв, книга йшла без title. */
    @Test
    void longTitleFitsUnderTheGameLimit() {
        String title = ChatColor.GRAY + "Рецепт: " + ChatColor.AQUA + "Mysticism Magister"
                + ChatColor.GRAY + " (5)";
        assertTrue(title.length() > 32, "тест беззмістовний, якщо вхід уже влазить");
        assertTrue(BookTitles.fit(title).length() <= 32);
    }

    /**
     * Інваріант: назва Послідовності мусить долітати до заголовка ЦІЛОЮ.
     * "Matriarch of Desolation" (23 символи) — найдовша в PathwayManager.
     */
    @Test
    void longestSequenceNameSurvivesInBothBookTitles() {
        String longest = "Matriarch of Desolation";

        String recipe = BookTitles.fit(ChatColor.GRAY + "Рецепт: " + ChatColor.AQUA + longest);
        String revelation = BookTitles.fit(ChatColor.GRAY + "Слід: " + ChatColor.LIGHT_PURPLE + longest);

        assertTrue(recipe.length() <= 32 && recipe.endsWith(longest), recipe);
        assertTrue(revelation.length() <= 32 && revelation.endsWith(longest), revelation);
    }

    @Test
    void veryLongTitleIsTruncated() {
        assertEquals(32, BookTitles.fit("Х".repeat(80)).length());
    }
}
