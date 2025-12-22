package me.vangoo.domain.abilities.core;


import me.vangoo.domain.entities.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;


public interface IAbilityContext {
    // ==================== CASTER ====================
    UUID getCasterId();

    Beyonder getCasterBeyonder();

    Location getCasterLocation();

    Player getCaster();  // ← Повний доступ до Bukkit Player!

    // ==================== TARGETING ====================
    List<LivingEntity> getNearbyEntities(double radius);

    List<Player> getNearbyPlayers(double radius);

    Optional<LivingEntity> getTargetedEntity(double maxRange);

    Optional<Player> getTargetedPlayer(double maxRange);

    // ==================== EFFECTS (Bukkit types!) ====================
    void spawnParticle(Particle type, Location loc, int count);

    void spawnParticle(Particle type, Location loc, int count,
                       double offsetX, double offsetY, double offsetZ);

    void playSound(Location loc, Sound sound, float volume, float pitch);

    void playSoundToCaster(Sound sound, float volume, float pitch);

    void applyEffect(UUID entityId, PotionEffectType effect, int durationTicks, int amplifier);

    void removeEffect(UUID entityId, PotionEffectType effect);

    void removeAllEffects(UUID entityId);

    // ==================== ACTIONS ====================
    void teleport(UUID entityId, Location location);

    void damage(UUID entityId, double amount);

    void heal(UUID entityId, double amount);

    // ==================== SCHEDULING ====================
    void scheduleDelayed(Runnable task, long delayTicks);

    void scheduleRepeating(Runnable task, long delayTicks, long periodTicks);

    // ==================== COOLDOWN ====================
    boolean hasCooldown(Ability ability);

    long getRemainingCooldownSeconds(Ability ability);

    void setCooldown(Ability ability, long durationTicks);

    void clearCooldown(Ability ability);

    // ==================== MESSAGES ====================
    void sendMessage(UUID playerId, String message);

    void sendMessageToCaster(String message);

    // ==================== BEYONDER ====================
    void updateSanityLoss(UUID playerId, int change);

    void lockAbilities(UUID playerId, int durationSeconds);

    // =INVENTORY AND ITEMS=
    void giveItem(HumanEntity entity, ItemStack item);

    boolean removeItem(HumanEntity entity, ItemStack item);

    // ==================== GLOWING ENTITIES ====================
    void setGlowing(UUID entityId, ChatColor color, int durationTicks);

    void setGlowingPermanent(UUID entityId, ChatColor color);

    void removeGlowing(UUID entityId);

    void setMultipleGlowing(List<UUID> entityIds, ChatColor color, int durationTicks);
}
