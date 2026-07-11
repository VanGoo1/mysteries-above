package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.CustomItem;

/**
 * Перетворює ринковий itemKey (custom:/recipe:/characteristic:) на людську
 * укр-назву для меню. Головний шлях — інгредієнти (custom:), назву яких дає
 * CustomItemService; решта — форматований рядок, невідоме — олюднений хвіст ключа.
 */
public class MarketItemNamer {

    private final CustomItemService customItems;

    public MarketItemNamer(CustomItemService customItems) {
        this.customItems = customItems;
    }

    public String displayName(String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return "?";
        }
        if (itemKey.startsWith("custom:")) {
            String id = itemKey.substring("custom:".length());
            if (customItems != null) {
                return customItems.getItem(id).map(CustomItem::displayName).orElseGet(() -> humanize(id));
            }
            return humanize(id);
        }
        String[] parts = itemKey.split(":");
        if (itemKey.startsWith("recipe:") && parts.length == 3) {
            return "Книга рецептів (" + parts[1] + ", Посл. " + parts[2] + ")";
        }
        if (itemKey.startsWith("characteristic:") && parts.length == 3) {
            return "Характеристика (" + parts[1] + ", Посл. " + parts[2] + ")";
        }
        return humanize(itemKey);
    }

    static String humanize(String raw) {
        String tail = raw.contains(":") ? raw.substring(raw.lastIndexOf(':') + 1) : raw;
        String spaced = tail.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
