package me.vangoo.managers;

import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityManager {

    private final CooldownManager cooldownManager;
    private final RampagerManager rampagerManager;

    /**
     * Карта для відстеження гравців з заблокованими здібностями та часу закінчення блокування.
     */
    private final Map<UUID, Long> lockedPlayers = new ConcurrentHashMap<>();



    public AbilityManager(CooldownManager cooldownManager, RampagerManager rampagerManager) {
        this.cooldownManager = cooldownManager;
        this.rampagerManager = rampagerManager;
    }

    public boolean executeAbility(Player caster, Beyonder beyonder, Ability ability) {

        if (isLocked(caster)) {
            return false;
        }

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
                    caster.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
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

    public void lockPlayer(Player player, int durationSeconds) {
        long expirationTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        lockedPlayers.put(player.getUniqueId(), expirationTime);
    }

    private boolean isLocked(Player player) {
        Long expirationTime = lockedPlayers.get(player.getUniqueId());

        if (expirationTime == null) {
            return false; // Гравець не заблокований
        }

        if (System.currentTimeMillis() > expirationTime) {
            lockedPlayers.remove(player.getUniqueId()); // Час блокування минув
            return false;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.DARK_PURPLE + "Ваші здібності тимчасово заблоковані!"));
        return true;
    }

}