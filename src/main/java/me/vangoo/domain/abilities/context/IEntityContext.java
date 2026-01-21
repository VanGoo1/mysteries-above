package me.vangoo.domain.abilities.context;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;


public interface IEntityContext {
    void teleport(UUID entityId, Location location);

    void damage(UUID entityId, double amount);

    void heal(UUID entityId, double amount);

    void applyPotionEffect(UUID entityId, PotionEffectType effect, int durationTicks, int amplifier);

    void removePotionEffect(UUID entityId, PotionEffectType effect);

    void removeAllPotionEffects(UUID entityId);

    void consumeItem(UUID humanEntityId, ItemStack item);

    void dropItem(UUID humanEntityId, ItemStack item);

    void giveItem(UUID humanEntityId, ItemStack item);

    void setHidden(UUID playerId, boolean hidden);

    void hidePlayerFromTarget(UUID playerId, UUID playerToHide);

    void showPlayerToTarget(UUID playerId, UUID playerToShowId);

    void setGameMode(UUID entityId, GameMode gameMode);

    void leaveVehicle(UUID targetId);

    void setVelocity(UUID targetId, Vector vector);

    void setSprinting(UUID targetId, boolean value);

    void giveExperience(UUID entityId, int amount);
}
