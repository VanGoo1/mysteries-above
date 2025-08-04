package me.vangoo.abilities;

import me.vangoo.LotmPlugin;
import me.vangoo.beyonders.Beyonder;
import me.vangoo.pathways.Pathway;
import org.bukkit.Bukkit;
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
        LotmPlugin plugin = (LotmPlugin) Bukkit.getPluginManager().getPlugin("LOTM-Plugin");
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        if (!meta.hasLore()) return null;

        plugin.getLogger().info("Looking for ability ");
        for (Ability a : pathway.GetAbilitiesForSequence(sequence)) {
            plugin.getLogger().info(a.getName());
        }
        return pathway.GetAbilitiesForSequence(sequence).stream().filter(a -> a.getName().equalsIgnoreCase(ChatColor.stripColor(item.getItemMeta().getDisplayName()))).findFirst().orElse(null);
    }


}
