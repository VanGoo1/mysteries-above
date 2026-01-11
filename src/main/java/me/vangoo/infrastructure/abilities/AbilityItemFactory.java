package me.vangoo.infrastructure.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

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

            List<String> descriptionLines = splitDescriptionByMarker(ability.getDescription(userSequence));
            for (String line : descriptionLines) {
                lore.add(ChatColor.GRAY + line);
            }

            if (ability.getType() == AbilityType.ACTIVE) {
                lore.add(ChatColor.GRAY + "Кулдаун: " + ChatColor.BLUE + ability.getCooldown(userSequence) + "c");

                // Display cost based on ability type
                int immediateCost = ability.getSpiritualityCost();
                int periodicCost = ability.getPeriodicCost();

                if (periodicCost > 0) {
                    // Channeled/toggle ability with periodic cost
                    lore.add(ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + periodicCost + "/сек");
                } else {
                    // Instant ability with immediate cost
                    lore.add(ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + immediateCost);
                }
            }

            try {
                CustomModelDataComponent comp = meta.getCustomModelDataComponent();
                String modelKey = switch (ability.getType()) {
                    case ACTIVE -> "active";
                    case TOGGLEABLE_PASSIVE -> "passive";
                    case PERMANENT_PASSIVE -> "permanent_passive";
                };
                comp.setStrings(java.util.List.of(modelKey));
                meta.setCustomModelDataComponent(comp);
            } catch (Throwable ignored) {
            }

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

        // Search in main abilities
        for (Ability ability : beyonder.getAbilities()) {
            if (ability.getName().equalsIgnoreCase(displayName)) {
                return ability;
            }
        }

        // Search in off-pathway abilities
        for (Ability ability : beyonder.getOffPathwayActiveAbilities()) {
            if (ability.getName().equalsIgnoreCase(displayName)) {
                return ability;
            }
        }

        return null;
    }

    /**
     * Розбиває опис на рядки по маркеру "/n".
     * Повертає список рядків без провідних/кінцевих пробілів; порожні частини ігноруються.
     */
    private List<String> splitDescriptionByMarker(String description) {
        List<String> lines = new ArrayList<>();
        if (description == null || description.isEmpty()) {
            return lines;
        }

        // Якщо в описі є справжні перенос рядка (\n), теж їх обробимо разом із маркером "/n"
        // Спочатку замінимо всі реальні newlines на маркер, щоб уніфікувати розбиття.
        String normalized = description.replace("\r\n", "/n").replace("\n", "/n").replace("\r", "/n");

        String[] parts = normalized.split("/n");
        for (String part : parts) {
            if (part == null) continue;
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    public boolean isAbilityItem(ItemStack item, Beyonder beyonder) {
        return getAbilityFromItem(item, beyonder) != null;
    }
}
