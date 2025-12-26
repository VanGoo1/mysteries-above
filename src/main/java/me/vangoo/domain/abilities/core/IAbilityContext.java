package me.vangoo.domain.abilities.core;


import me.vangoo.domain.entities.Beyonder;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nullable;


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

    boolean rescueFromRampage(UUID casterId, UUID targetId);

    boolean isSneaking(UUID targetId);

    boolean hasItem(Material material, int amount);

    void consumeItem(Material material, int amount);

    Map<String, String> getTargetAnalysis(UUID targetId);

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

    @Nullable
    Beyonder getBeyonderFromEntity(UUID entityId);

    boolean isBeyonder(UUID entityId);

    Optional<Integer> getEntitySequenceLevel(UUID entityId);

    // =INVENTORY AND ITEMS=
    void giveItem(HumanEntity entity, ItemStack item);

    boolean removeItem(HumanEntity entity, ItemStack item);

    // ==================== GLOWING ENTITIES ====================
    void setGlowing(UUID entityId, ChatColor color, int durationTicks);

    void setGlowingPermanent(UUID entityId, ChatColor color);

    void removeGlowing(UUID entityId);

    void setMultipleGlowing(List<UUID> entityIds, ChatColor color, int durationTicks);

    // ==================== SURFACE DATA ====================
    Location getBedSpawnLocation(UUID targetId);

    long getPlayTimeHours(UUID targetId);

    String getMainHandItemName(UUID targetId);

    int getDeathsStatistic(UUID targetId);

    // ==================== DEEP DATA ====================
    List<String> getEnderChestContents(UUID targetId, int limit);

    int getPlayerKills(UUID targetId);

    int getVillagerKills(UUID targetId);

    Location getLastDeathLocation(UUID targetId);

    int getExperienceLevel(UUID targetId);

    int getBeyonderMastery(UUID targetId);

    // ==========================================
    // VISUAL EFFECTS (EffectLib)
    // ==========================================

    /**
     * Create a sphere effect at location
     *
     * @param location      Center of sphere
     * @param radius        Radius of sphere
     * @param particle      Particle type to use
     * @param durationTicks How long effect lasts
     */
    void playSphereEffect(Location location, double radius, Particle particle, int durationTicks);

    /**
     * Create a helix/spiral effect between two points
     *
     * @param start         Start location
     * @param end           End location
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playHelixEffect(Location start, Location end, Particle particle, int durationTicks);

    /**
     * Create a circle effect at location
     *
     * @param location      Center of circle
     * @param radius        Radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playCircleEffect(Location location, double radius, Particle particle, int durationTicks);

    /**
     * Create a line effect between two points
     *
     * @param start    Start location
     * @param end      End location
     * @param particle Particle type
     */
    void playLineEffect(Location start, Location end, Particle particle);

    /**
     * Create a cone effect (useful for directional abilities)
     *
     * @param apex          Tip of cone
     * @param direction     Direction cone points
     * @param angle         Cone opening angle in degrees
     * @param length        Length of cone
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playConeEffect(Location apex, org.bukkit.util.Vector direction, double angle,
                        double length, Particle particle, int durationTicks);

    /**
     * Create a vortex/tornado effect
     *
     * @param location      Center location
     * @param height        Height of vortex
     * @param radius        Base radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playVortexEffect(Location location, double height, double radius,
                          Particle particle, int durationTicks);

    /**
     * Create a wave effect emanating from location
     *
     * @param center        Center point
     * @param radius        Wave radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playWaveEffect(Location center, double radius, Particle particle, int durationTicks);

    /**
     * Create a cube outline effect
     *
     * @param location      Center of cube
     * @param size          Size of cube edges
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playCubeEffect(Location location, double size, Particle particle, int durationTicks);

    /**
     * Create an animated trail effect following an entity
     *
     * @param entityId      Entity to follow
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playTrailEffect(UUID entityId, Particle particle, int durationTicks);

    /**
     * Create a beam effect between two locations (laser-like)
     *
     * @param start         Start location
     * @param end           End location
     * @param particle      Particle type
     * @param width         Beam width
     * @param durationTicks Duration
     */
    void playBeamEffect(Location start, Location end, Particle particle,
                        double width, int durationTicks);

    /**
     * Create an explosion ring effect
     *
     * @param center   Center of explosion
     * @param radius   Ring radius
     * @param particle Particle type
     */
    void playExplosionRingEffect(Location center, double radius, Particle particle);

    /**
     * Стежить за гравцем протягом певного часу.
     *
     * @param targetId      кого перевіряємо
     * @param durationTicks скільки часу чекаємо (в тіках)
     * @param callback      викликається з true, якщо гравець присів, і false, якщо час вийшов
     */
    void monitorSneaking(UUID targetId, int durationTicks, Consumer<Boolean> callback);

    /**
     * Відкрити GUI вибору
     */
    <T> void openChoiceMenu(
            String title,
            List<T> choices,
            Function<T, ItemStack> itemMapper,
            Consumer<T> onSelect
    );

    /**
     * Підписатися на тимчасову подію
     */
    <T extends Event> void subscribeToEvent(
            Class<T> eventClass,
            Predicate<T> filter,
            Consumer<T> handler,
            int durationTicks
    );

    int getMinedAmount(UUID targetId, Material oreType);

    int getUsedAmount(UUID targetId, Material itemType);
}
