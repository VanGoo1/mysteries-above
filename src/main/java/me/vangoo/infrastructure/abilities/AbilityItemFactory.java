package me.vangoo.infrastructure.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AbilityItemFactory {

    public ItemStack getItemFromAbility(Ability ability, Sequence userSequence) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ability.getName());
            List<String> lore = new ArrayList<>(List.of(
                    ChatColor.GRAY + "--------------------------------------------"
            ));

            List<String> descriptionLines = splitTextIntoLines(ability.getDescription(userSequence), 60);
            for (String line : descriptionLines) {
                lore.add(ChatColor.GRAY + line);
            }

            if (ability.getType() == AbilityType.ACTIVE) {
                lore.add(ChatColor.GRAY + "Кулдаун: " + ChatColor.BLUE + ability.getCooldown(userSequence) + "c");
                lore.add(ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + ability.getSpiritualityCost());
            }
//            additionalStatsLore.forEach((key, value) -> {
//                lore.add(ChatColor.GRAY + key + ": " + ChatColor.BLUE + value);
//            });

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }


    @Nullable
    public Ability getAbilityFromItem(ItemStack item, Beyonder beyonder) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();

        for (Ability ability : beyonder.getAbilities()) {
            if (ability.getName().equalsIgnoreCase(displayName)) {
                return ability;
            }
        }

        return null;
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

    public boolean isAbilityItem(ItemStack item, Beyonder beyonder) {
        return getAbilityFromItem(item, beyonder) != null;
    }
}
