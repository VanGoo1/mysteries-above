package me.vangoo.managers;

import me.vangoo.domain.Ability;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbilityItemFactory {

    public ItemStack createItem(Ability ability, Map<String, String> additionalStatsLore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ability.getName());
            List<String> lore = new ArrayList<>(List.of(
                    ChatColor.GRAY + "--------------------------------------------"
            ));

            List<String> descriptionLines = splitTextIntoLines(ability.getDescription(), 60);
            for (String line : descriptionLines) {
                lore.add(ChatColor.GRAY + line);
            }

            if (!ability.isPassive()) {
                lore.add(ChatColor.GRAY + "Кулдаун: " + ChatColor.BLUE + ability.getCooldown() + "c");
                lore.add(ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + ability.getSpiritualityCost());
            }
            additionalStatsLore.forEach((key, value) -> {
                lore.add(ChatColor.GRAY + key + ": " + ChatColor.BLUE + value);
            });

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> splitTextIntoLines(String text, int maxLength) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        // Розділяємо текст по словах
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // Перевіряємо, чи поміститься слово в поточному рядку
            if (currentLine.length() + word.length() + 1 <= maxLength) {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                // Якщо поточний рядок не порожній, додаємо його до списку
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                // Якщо слово довше за максимальну довжину, розбиваємо його
                if (word.length() > maxLength) {
                    while (word.length() > maxLength) {
                        lines.add(word.substring(0, maxLength));
                        word = word.substring(maxLength);
                    }
                    currentLine.append(word);
                } else {
                    currentLine.append(word);
                }
            }
        }

        // Додаємо останній рядок, якщо він не порожній
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
