package me.vangoo.domain.abilities.core;

import me.vangoo.domain.abilities.context.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.RecordedEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface IAbilityContext {

    // ==================== CASTER ====================

    UUID getCasterId();

    Beyonder getCasterBeyonder();

    Location getCasterLocation();

    Player getCasterPlayer();

    Location getCasterEyeLocation();

    IVisualEffectsContext effects();
    ISchedulingContext scheduling();
    IDataContext playerData();
    IBeyonderContext beyonder();
    IUIContext ui();
    ITargetContext targeting();
    IEventContext events();
    ICooldownContext cooldown();
    IRampageContext rampage();
    IEntityContext entity();
    IGlowingContext glowing();
    IMessagingContext messaging();

    // ==================== TARGETING ====================

    @Deprecated
    List<LivingEntity> getNearbyEntities(double radius);

    @Deprecated
    List<Player> getNearbyPlayers(double radius);

    @Deprecated
    Optional<LivingEntity> getTargetedEntity(double maxRange);

    @Deprecated
    Optional<Player> getTargetedPlayer(double maxRange);

    @Deprecated
    boolean hasItem(Material material, int amount);

    // ==================== EFFECTS (Bukkit types!) ====================

    @Deprecated
    void spawnParticle(Particle type, Location loc, int count);

    @Deprecated
    void spawnParticle(Particle type, Location loc, int count,
                       double offsetX, double offsetY, double offsetZ);

    @Deprecated
    void playSound(Location loc, Sound sound, float volume, float pitch);

    @Deprecated
    void playSoundToCaster(Sound sound, float volume, float pitch);

    @Deprecated
    void applyEffect(UUID entityId, PotionEffectType effect, int durationTicks, int amplifier);

    @Deprecated
    void removeEffect(UUID entityId, PotionEffectType effect);

    @Deprecated
    void removeAllEffects(UUID entityId);

    // ==================== ACTIONS ====================

    @Deprecated
    void teleport(UUID entityId, Location location);

    @Deprecated
    void damage(UUID entityId, double amount);

    @Deprecated
    void heal(UUID entityId, double amount);

    // ==================== SCHEDULING ====================

    @Deprecated
    BukkitTask scheduleDelayed(Runnable task, long delayTicks);

    @Deprecated
    BukkitTask scheduleRepeating(Runnable task, long delayTicks, long periodTicks);

    @Deprecated
    void runAsync(Runnable task);

    // ==================== COOLDOWN ====================

    @Deprecated
    boolean hasCooldown(Ability ability);

    @Deprecated
    long getRemainingCooldownSeconds(Ability ability);

    @Deprecated
    void setCooldown(Ability ability, long durationTicks);

    // ==================== MESSAGES ====================

    @Deprecated
    void sendMessage(UUID playerId, String message);

    @Deprecated
    void sendMessageToCaster(String message);

    @Deprecated
    void sendMessageToActionBar(Component message);

    @Deprecated
    void sendMessageToActionBar(Player target, Component message);

    @Deprecated
    void spawnTemporaryHologram(Location location, Component text, long durationTicks);

    @Deprecated
    void spawnFollowingHologramForPlayer(Player viewer, Player target, Component text,
                                         long durationTicks, long updateIntervalTicks);

    // ==================== BEYONDER ====================

    @Deprecated
    void updateSanityLoss(UUID playerId, int change);

    @Deprecated
    void lockAbilities(UUID playerId, int durationSeconds);

    @Deprecated
    @Nullable
    Beyonder getBeyonderFromEntity(UUID entityId);

    @Deprecated
    boolean isBeyonder(UUID entityId);

    @Deprecated
    Optional<Integer> getEntitySequenceLevel(UUID entityId);

    // ==================== INVENTORY ====================

    @Deprecated
    void giveItem(HumanEntity entity, ItemStack item);

    @Deprecated
    boolean removeItem(HumanEntity entity, ItemStack item);

    // ==================== GLOWING ====================

    @Deprecated
    void setGlowing(UUID entityId, ChatColor color, int durationTicks);

    @Deprecated
    void removeGlowing(UUID entityId);

    @Deprecated
    void setMultipleGlowing(List<UUID> entityIds, ChatColor color, int durationTicks);

    // ==================== DEEP DATA ====================

    @Deprecated
    List<String> getEnderChestContents(UUID targetId, int limit);

    // ==================== VISUAL EFFECTS (EffectLib) ====================

    @Deprecated
    void playSphereEffect(Location location, double radius, Particle particle, int durationTicks);

    @Deprecated
    void playHelixEffect(Location start, Location end, Particle particle, int durationTicks);

    @Deprecated
    void playCircleEffect(Location location, double radius, Particle particle, int durationTicks);

    @Deprecated
    void playLineEffect(Location start, Location end, Particle particle);

    @Deprecated
    void playConeEffect(Location apex, org.bukkit.util.Vector direction, double angle,
                        double length, Particle particle, int durationTicks);

    @Deprecated
    void playVortexEffect(Location location, double height, double radius,
                          Particle particle, int durationTicks);

    @Deprecated
    void playWaveEffect(Location center, double radius, Particle particle, int durationTicks);

    @Deprecated
    void playCubeEffect(Location location, double size, Particle particle, int durationTicks);

    @Deprecated
    void playTrailEffect(UUID entityId, Particle particle, int durationTicks);

    @Deprecated
    void playBeamEffect(Location start, Location end, Particle particle,
                        double width, int durationTicks);

    @Deprecated
    void playExplosionRingEffect(Location center, double radius,
                                 Particle particle, Particle.DustOptions options);

    // ==================== UI / EVENTS ====================

    @Deprecated
    <T> void openChoiceMenu(
            String title,
            List<T> choices,
            Function<T, ItemStack> itemMapper,
            Consumer<T> onSelect
    );

    @Deprecated
    void publishAbilityUsedEvent(ActiveAbility activeAbility);

    @Deprecated
    void showPlayerToTarget(Player target, Player playerToShow);

    @Deprecated
    void hidePlayerFromTarget(Player target, Player playerToHide);

    @Deprecated
    boolean isAbilityActivated(UUID entityId, AbilityIdentity abilityIdentity);

    @Deprecated
    void removeOffPathwayAbility(AbilityIdentity identity);

    @Deprecated
    void subscribeToAbilityEvents(Consumer<AbilityDomainEvent> handler);

    @Deprecated
    void subscribeToAbilityEvents(
            Function<AbilityDomainEvent, Boolean> handler,
            int durationTicks
    );

    @Deprecated
    Optional<AbilityDomainEvent> getLastAbilityEvent(UUID casterId, int maxAgeSeconds);

    @Deprecated
    List<RecordedEvent> getPastEvents(Location location, int radius, int timeSeconds);

    @Deprecated
    int getKnownRecipeCount(String pathwayName);
}
