package me.vangoo.managers;

import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AbilityManager {

    CooldownManager cooldownManager;
    private final RampagerManager rampagerManager;

    public AbilityManager(CooldownManager cooldownManager, RampagerManager rampagerManager) {
        this.cooldownManager = cooldownManager;
        this.rampagerManager = rampagerManager;
    }

    public boolean executeAbility(Player caster, Beyonder beyonder, Ability ability) {
        if (beyonder.getSpirituality() < ability.getSpiritualityCost()) {
            caster.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Недостатньо духовності"));
            return false;
        }

        if (cooldownManager.isOnCooldown(caster.getUniqueId(), ability)) {
            caster.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Кулдаун: " + cooldownManager.getRemainingCooldown(caster.getUniqueId(), ability) + "c"));
            return false;
        }

        if (rampagerManager.executeLossOfControl(caster, beyonder)) {
            cooldownManager.setCooldown(caster.getUniqueId(), ability);
            return false;
        }

        boolean result = ability.execute(caster, beyonder);
        if (result) {
            cooldownManager.setCooldown(caster.getUniqueId(), ability);
            if (!ability.isPassive()) {
                beyonder.setMastery(beyonder.getMastery() + 1);
                beyonder.updateMaxSpirituality();
                beyonder.DecrementSpirituality(ability.getSpiritualityCost());
                if (beyonder.getSpirituality() <= beyonder.getMaxSpirituality() * 0.05) {
                    beyonder.setSanityLossScale(beyonder.getSanityLossScale() + 1);
                }
            }
        }

        return result;
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
