package me.vangoo.abilities;

import me.vangoo.beyonders.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.LocalDateTime;

public class AbilityManager {

    public AbilityManager() {
    }

    public boolean executeAbility(Player caster, Beyonder beyonder, Ability ability) {
        if (beyonder.getSpirituality() < ability.getSpiritualityCost()) {
            caster.sendMessage(ChatColor.RED + "Недостатньо духовності");
            return false;
        }
        Duration duration = Duration.between(ability.getLastUse(), LocalDateTime.now());
        if (duration.toSeconds() < ability.getCooldown() / 20) {
            caster.sendMessage(ChatColor.RED + "Здібність не готова");
            return false;
        }else {
            ability.updateLastUse();
        }

        return ability.execute(caster, beyonder);
    }

    public Ability GetAbilityFromItem(ItemStack item, Beyonder beyonder) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;
        String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();

        for (Ability ability : beyonder.getAbilities()) {
            if (ability.getName().equalsIgnoreCase(displayName)) {
                return ability;
            }
        }

        return null;
    }
}
