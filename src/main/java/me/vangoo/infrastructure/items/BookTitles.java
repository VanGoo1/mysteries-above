package me.vangoo.infrastructure.items;

import org.bukkit.ChatColor;

/**
 * Заголовок книги: {@code BookMeta.setTitle} мовчки ВІДХИЛЯЄ рядок довший за 32 символи
 * (кольорові коди рахуються), а written_book без заголовка валить кодування пакета
 * NPE-ю — гравця кикає, предмет не зберігається. Тож усі книги плагіна проганяють
 * заголовок через це місце.
 */
public final class BookTitles {

    private static final int MAX_LENGTH = 32; // CraftMetaBook.MAX_TITLE_LENGTH

    private BookTitles() {
    }

    public static String fit(String title) {
        if (title.length() <= MAX_LENGTH) return title;
        String plain = ChatColor.stripColor(title);
        return plain.length() <= MAX_LENGTH ? plain : plain.substring(0, MAX_LENGTH);
    }
}
