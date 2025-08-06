package me.vangoo.abilities;

import me.vangoo.beyonders.Beyonder;
import me.vangoo.pathways.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AbilityManager {

    public AbilityManager() {
    }

    public boolean executeAbility(Player caster, Beyonder beyonder, Ability ability) {
        if (beyonder.getSpirituality() < ability.getSpiritualityCost()) {
            return false;
        }
        ability.execute(caster, beyonder);
        return true;
    }

    public Ability GetAbilityFromItem(ItemStack item, int sequence, Pathway pathway) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;
        String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();

        for (int seq = sequence; seq >= 0; seq--) {
            for (Ability ability : pathway.GetAbilitiesForSequence(seq)) {
                if (ability.getName().equalsIgnoreCase(displayName)) {
                    return ability;
                }
            }
        }
        return null;
    }
}
