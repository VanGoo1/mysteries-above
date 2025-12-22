package me.vangoo.application.services;


import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.logging.Logger;


public class BukkitAbilityContext implements IAbilityContext {
    private final Player caster;
    private final World world;
    private final MysteriesAbovePlugin plugin;
    private final CooldownManager cooldownManager;
    private final BeyonderService beyonderService;
    private final AbilityLockManager lockManager;
    private final GlowingEntities glowingEntities;
    private static final Logger LOGGER = Logger.getLogger(BukkitAbilityContext.class.getName());
    // Cache for performance (valid only during single ability execution)
    private final Map<UUID, Entity> entityCache = new HashMap<>();

    public BukkitAbilityContext(
            Player caster,
            MysteriesAbovePlugin plugin,
            CooldownManager cooldownManager,
            BeyonderService beyonderService,
            AbilityLockManager lockManager, GlowingEntities glowingEntities
    ) {
        this.caster = caster;
        this.world = caster.getWorld();
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.beyonderService = beyonderService;
        this.lockManager = lockManager;
        this.glowingEntities = glowingEntities;
    }

    // ==========================================
    // CASTER
    // ==========================================

    @Override
    public UUID getCasterId() {
        return caster.getUniqueId();
    }

    @Override
    public Beyonder getCasterBeyonder() {
        return beyonderService.getBeyonder(caster.getUniqueId());
    }

    @Override
    public Location getCasterLocation() {
        return caster.getLocation();
    }

    @Override
    public Player getCaster() {
        return caster;
    }

    // ==========================================
    // TARGETING
    // ==========================================

    @Override
    public List<LivingEntity> getNearbyEntities(double radius) {
        List<LivingEntity> result = new ArrayList<>();

        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.equals(caster)) continue;
            if (entity.isDead()) continue;

            result.add(living);

            // Cache for future operations
            entityCache.put(entity.getUniqueId(), entity);
        }

        return result;
    }

    @Override
    public List<Player> getNearbyPlayers(double radius) {
        List<Player> result = new ArrayList<>();

        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player player)) continue;
            if (entity.equals(caster)) continue;
            if (player.getGameMode() == GameMode.SPECTATOR) continue;

            result.add(player);
            entityCache.put(entity.getUniqueId(), entity);
        }

        return result;
    }

    @Override
    public Optional<LivingEntity> getTargetedEntity(double maxRange) {
        RayTraceResult result = world.rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                maxRange,
                entity -> entity instanceof LivingEntity && !entity.equals(caster)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity living) {
            entityCache.put(living.getUniqueId(), living);
            return Optional.of(living);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Player> getTargetedPlayer(double maxRange) {
        RayTraceResult result = world.rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                maxRange,
                entity -> entity instanceof Player && !entity.equals(caster)
        );

        if (result != null && result.getHitEntity() instanceof Player player) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                entityCache.put(player.getUniqueId(), player);
                return Optional.of(player);
            }
        }

        return Optional.empty();
    }

    // ==========================================
    // EFFECTS
    // ==========================================

    @Override
    public void spawnParticle(Particle type, Location loc, int count) {
        world.spawnParticle(type, loc, count);
    }

    @Override
    public void spawnParticle(Particle type, Location loc, int count,
                              double offsetX, double offsetY, double offsetZ) {
        world.spawnParticle(type, loc, count, offsetX, offsetY, offsetZ);
    }

    @Override
    public void playSound(Location loc, Sound sound, float volume, float pitch) {
        world.playSound(loc, sound, volume, pitch);
    }

    @Override
    public void playSoundToCaster(Sound sound, float volume, float pitch) {
        caster.playSound(caster.getLocation(), sound, volume, pitch);
    }

    @Override
    public void applyEffect(UUID entityId, PotionEffectType effect, int durationTicks, int amplifier) {
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            living.addPotionEffect(new PotionEffect(effect, durationTicks, amplifier));
        }
    }

    @Override
    public void removeEffect(UUID entityId, PotionEffectType effect) {
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            living.removePotionEffect(effect);
        }
    }

    @Override
    public void removeAllEffects(UUID entityId) {
        Entity entity = getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            for (PotionEffect activeEffect : living.getActivePotionEffects()) {
                living.removePotionEffect(activeEffect.getType());
            }
        }
    }

    // ==========================================
    // ACTIONS
    // ==========================================

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

    // ==========================================
    // SCHEDULING
    // ==========================================

    @Override
    public void scheduleDelayed(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void scheduleRepeating(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    // ==========================================
    // COOLDOWN
    // ==========================================

    @Override
    public boolean hasCooldown(Ability ability) {
        return cooldownManager.isOnCooldown(caster.getUniqueId(), ability);
    }

    @Override
    public long getRemainingCooldownSeconds(Ability ability) {
        return cooldownManager.getRemainingCooldown(caster.getUniqueId(), ability);
    }

    @Override
    public void setCooldown(Ability ability, long durationTicks) {
        cooldownManager.setCooldown(caster.getUniqueId(), ability);
    }

    @Override
    public void clearCooldown(Ability ability) {
        cooldownManager.clearCooldown(caster.getUniqueId(), ability);
    }

    // ==========================================
    // MESSAGES
    // ==========================================

    @Override
    public void sendMessage(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void sendMessageToCaster(String message) {
        caster.sendMessage(message);
    }

    // ==========================================
    // BEYONDER
    // ==========================================

    @Override
    public void updateSanityLoss(UUID playerId, int change) {
        var beyonder = beyonderService.getBeyonder(playerId);
        if (beyonder != null) {
            if (change > 0) {
                beyonder.increaseSanityLoss(change);
            } else if (change < 0) {
                beyonder.decreaseSanityLoss(-change);
            }
            beyonderService.updateBeyonder(beyonder);
        }
    }

    @Override
    public void lockAbilities(UUID playerId, int durationSeconds) {
        lockManager.lockPlayer(playerId, durationSeconds);
    }
//
//    @Override
//    public boolean isPassiveAbilityEnabled(String abilityName) {
//        return passiveAbilityManager.isToggleableEnabled(getCasterId(), abilityName);
//    }

    // ==========================================
    // INVENTORY
    // ==========================================

    @Override
    public void giveItem(HumanEntity entity, ItemStack item) {
        Inventory inventory = entity.getInventory();

        // Try to add to inventory
        HashMap<Integer, ItemStack> leftover = inventory.addItem(item);

        // If inventory full - drop on ground
        if (!leftover.isEmpty()) {
            Location loc = entity.getLocation();
            for (ItemStack drop : leftover.values()) {
                world.dropItem(loc, drop);
            }
        }
    }

    @Override
    public boolean removeItem(HumanEntity entity, ItemStack item) {
        Inventory inventory = entity.getInventory();

        // Find slot with this item
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);

            if (current != null && current.isSimilar(item)) {
                // Decrease amount or remove completely
                if (current.getAmount() > item.getAmount()) {
                    current.setAmount(current.getAmount() - item.getAmount());
                    inventory.setItem(i, current);
                } else {
                    inventory.setItem(i, null);
                }
                return true;
            }
        }

        return false;
    }

    // ==========================================
    // GLOWING ENTITIES
    // ==========================================

    @Override
    public void setGlowing(UUID entityId, ChatColor color, int durationTicks) {
        Entity entity = getEntity(entityId);
        if (entity == null) return;

        try {
            // Set glowing
            glowingEntities.setGlowing(entity, caster, color);

            // Schedule automatic removal
            scheduleDelayed(() -> {
                removeGlowing(entityId);
            }, durationTicks);

        } catch (ReflectiveOperationException e) {
            LOGGER.warning("Failed to set glowing for entity " + entityId + ": " + e.getMessage());
        }
    }

    @Override
    public void setGlowingPermanent(UUID entityId, ChatColor color) {
        Entity entity = getEntity(entityId);
        if (entity == null) return;

        try {
            glowingEntities.setGlowing(entity, caster, color);
        } catch (ReflectiveOperationException e) {
            LOGGER.warning("Failed to set permanent glowing for entity " + entityId + ": " + e.getMessage());
        }
    }

    @Override
    public void removeGlowing(UUID entityId) {
        Entity entity = getEntity(entityId);
        if (entity == null || !entity.isValid()) return;

        try {
            glowingEntities.unsetGlowing(entity, caster);
        } catch (ReflectiveOperationException e) {
            LOGGER.warning("Failed to remove glowing from entity " + entityId + ": " + e.getMessage());
        }
    }

    @Override
    public void setMultipleGlowing(List<UUID> entityIds, ChatColor color, int durationTicks) {
        for (UUID entityId : entityIds) {
            setGlowing(entityId, color, durationTicks);
        }
    }


    // ==========================================
    // PRIVATE HELPERS
    // ==========================================

    /**
     * Get entity by UUID with caching
     */
    private Entity getEntity(UUID entityId) {
        // Try cache first (fast)
        Entity cached = entityCache.get(entityId);
        if (cached != null && cached.isValid() && !cached.isDead()) {
            return cached;
        }

        // Fallback to Bukkit lookup (slow)
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            entityCache.put(entityId, entity);
            return entity;
        }

        // Entity not found or dead
        entityCache.remove(entityId);
        return null;
    }
}