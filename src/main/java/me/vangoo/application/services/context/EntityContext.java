package me.vangoo.application.services.context;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IEntityContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import static org.bukkit.Bukkit.getEntity;

public class EntityContext implements IEntityContext {

    private final MysteriesAbovePlugin plugin;

    public EntityContext(MysteriesAbovePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void teleport(UUID entityId, Location location) {
        Entity entity = getEntity(entityId);
        if (entity != null) {
            entity.teleport(location);
        }
    }

    @Override
    public void damage(UUID entityId, double amount) {
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            living.damage(amount);
        }
    }

    @Override
    public void heal(UUID entityId, double amount) {
        if (amount <= 0) return;
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            double maxHealth = Objects.requireNonNull(living.getAttribute(Attribute.MAX_HEALTH)).getValue();
            double newHealth = Math.min(living.getHealth() + amount, maxHealth);
            living.setHealth(newHealth);
        }
    }

    @Override
    public void applyPotionEffect(UUID entityId, PotionEffectType effect, int durationTicks, int amplifier) {
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            living.addPotionEffect(new PotionEffect(effect, durationTicks, amplifier));
        }
    }

    @Override
    public void removePotionEffect(UUID entityId, PotionEffectType effect) {

    }

    @Override
    public void removeAllPotionEffects(UUID entityId) {
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            for (PotionEffect activeEffect : living.getActivePotionEffects()) {
                living.removePotionEffect(activeEffect.getType());
            }
        }
    }

    @Override
    public void consumeItem(UUID humanEntityId, ItemStack item) {
        Entity entity = getEntity(humanEntityId);
        if (!(entity instanceof HumanEntity target) || item == null) return;

        Inventory inventory = target.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);

            if (current != null && current.isSimilar(item)) {
                int newAmount = current.getAmount() - item.getAmount();
                if (newAmount > 0) {
                    current.setAmount(newAmount);
                    inventory.setItem(i, current);
                } else {
                    inventory.setItem(i, null);
                }
                return; // Consume from one stack only and exit
            }
        }
    }

    @Override
    public void dropItem(UUID humanEntityId, ItemStack item) {
        Player player = Bukkit.getPlayer(humanEntityId);
        if (player == null || !player.isOnline()) return;
        if (item == null || item.getType() == Material.AIR) return;
        consumeItem(humanEntityId, item);
        player.getWorld().dropItem(player.getLocation(), item.clone());
    }

    @Override
    public void giveItem(UUID humanEntityId, ItemStack item) {
        Entity entity = getEntity(humanEntityId);
        if (!(entity instanceof HumanEntity humanEntity)) return;

        Inventory inventory = humanEntity.getInventory();

        HashMap<Integer, ItemStack> leftover = inventory.addItem(item);

        if (!leftover.isEmpty()) {
            Location loc = humanEntity.getLocation();
            for (ItemStack drop : leftover.values()) {
                humanEntity.getWorld().dropItem(loc, drop);
            }
        }
    }

    @Override
    public void setHidden(UUID playerId, boolean hidden) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        if (hidden) {
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (!target.getUniqueId().equals(playerId)) {
                    target.hidePlayer(plugin, player);
                }
            }
        } else {
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                target.showPlayer(plugin, player);
            }
        }
    }

    @Override
    public void hidePlayerFromTarget(UUID playerId, UUID playerToHide) {
        Player player = Bukkit.getPlayer(playerId);
        Player toHide = Bukkit.getPlayer(playerToHide);

        if (player != null && toHide != null) {
            player.hidePlayer(plugin, toHide);
        }
    }

    @Override
    public void showPlayerToTarget(UUID playerId, UUID playerToShowId) {
        Player player = Bukkit.getPlayer(playerId);
        Player toShow = Bukkit.getPlayer(playerToShowId);

        if (player != null && toShow != null) {
            player.showPlayer(plugin, toShow);
        }
    }
}