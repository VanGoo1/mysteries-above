package me.vangoo.application.services;


import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.*;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.rampager.RampageManager;
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
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
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
    private final EffectManager effectManager;
    private final Logger LOGGER;
    private final RampageManager rampageManager;
    // Cache for performance (valid only during single ability execution)
    private final Map<UUID, Entity> entityCache = new HashMap<>();

    public BukkitAbilityContext(
            Player caster,
            MysteriesAbovePlugin plugin,
            CooldownManager cooldownManager,
            BeyonderService beyonderService,
            AbilityLockManager lockManager, GlowingEntities glowingEntities, EffectManager effectManager, RampageManager rampageManager
    ) {
        this.caster = caster;
        this.world = caster.getWorld();
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.beyonderService = beyonderService;
        this.lockManager = lockManager;
        this.glowingEntities = glowingEntities;
        this.effectManager = effectManager;
        this.LOGGER = plugin.getLogger();
        this.rampageManager = rampageManager;
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

    @Override
    public boolean rescueFromRampage(UUID casterId, UUID targetId) {
        if (rampageManager.isInRampage(targetId)) {
            return rampageManager.rescueFromRampage(targetId, casterId);
        }
        return false;
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
        return cooldownManager.isOnCooldown(getCasterBeyonder(), ability);
    }

    @Override
    public long getRemainingCooldownSeconds(Ability ability) {
        return cooldownManager.getRemainingCooldown(getCasterBeyonder(), ability);
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

    // ==========================================
    // BEYONDER INFORMATION (for sequence checks)
    // ==========================================

    @Override
    @Nullable
    public Beyonder getBeyonderFromEntity(UUID entityId) {
        return beyonderService.getBeyonder(entityId);
    }

    @Override
    public boolean isBeyonder(UUID entityId) {
        return beyonderService.getBeyonder(entityId) != null;
    }

    @Override
    public Optional<Integer> getEntitySequenceLevel(UUID entityId) {
        Beyonder beyonder = beyonderService.getBeyonder(entityId);
        if (beyonder == null) {
            return Optional.empty();
        }
        return Optional.of(beyonder.getSequenceLevel());
    }

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
    // VISUAL EFFECTS (EffectLib)
    // ==========================================

    @Override
    public void playSphereEffect(Location location, double radius, Particle particle, int durationTicks) {
        SphereEffect effect = new SphereEffect(effectManager);
        effect.setLocation(location);
        effect.radius = (float) radius;
        effect.particle = particle;
        effect.particles = 50;
        effect.iterations = durationTicks;
        effect.period = 1;
        effectManager.start(effect);
    }

    @Override
    public void playHelixEffect(Location start, Location end, Particle particle, int durationTicks) {
        HelixEffect effect = new HelixEffect(effectManager);
        effect.setLocation(start);
        effect.setTarget(end);
        effect.particle = particle;
        effect.strands = 3;
        effect.radius = 0.5f;
        effect.curve = 10;
        effect.rotation = Math.PI / 4;
        effect.iterations = durationTicks;
        effect.period = 1;
        effectManager.start(effect);
    }

    @Override
    public void playCircleEffect(Location location, double radius, Particle particle, int durationTicks) {
        CircleEffect effect = new CircleEffect(effectManager);
        effect.setLocation(location);
        effect.radius = (float) radius;
        effect.particle = particle;
        effect.particles = 30;
        effect.iterations = durationTicks;
        effect.period = 1;
        effectManager.start(effect);
    }

    @Override
    public void playLineEffect(Location start, Location end, Particle particle) {
        LineEffect effect = new LineEffect(effectManager);
        effect.setLocation(start);
        effect.setTarget(end);
        effect.particle = particle;
        effect.particles = 20;
        effectManager.start(effect);
    }

    @Override
    public void playConeEffect(Location apex, Vector direction, double angle, double length, Particle particle, int durationTicks) {
        ConeEffect effect = new ConeEffect(effectManager);
        effect.setLocation(apex);
        effect.particle = particle;
        effect.lengthGrow = (float) (length / durationTicks);
        effect.radiusGrow = (float) (Math.tan(Math.toRadians(angle / 2)) * length / durationTicks);
        effect.particles = 30;
        effect.iterations = durationTicks;
        effect.period = 1;

        // Set direction
        Location target = apex.clone().add(direction.normalize().multiply(length));
        effect.setTarget(target);

        effectManager.start(effect);
    }

    @Override
    public void playVortexEffect(Location location, double height, double radius,
                                 Particle particle, int durationTicks) {
        VortexEffect effect = new VortexEffect(effectManager);
        effect.setLocation(location);
        effect.particle = particle;
        effect.radius = (float) radius;
        effect.grow = (float) height / durationTicks;
        effect.radials = 0.1f;
        effect.circles = 10;
        effect.helixes = 3;
        effect.iterations = durationTicks;
        effect.period = 1;
        effectManager.start(effect);
    }

    @Override
    public void playWaveEffect(Location center, double radius, Particle particle, int durationTicks) {
        CircleEffect effect = new CircleEffect(effectManager);
        effect.setLocation(center);
        effect.particle = particle;
        effect.particles = 40;
        effect.iterations = durationTicks;
        effect.period = 1;

        // Animate radius growth
        final double radiusPerTick = radius / durationTicks;
        final int[] currentIteration = {0};

        scheduleRepeating(() -> {
            if (currentIteration[0]++ >= durationTicks) {
                return;
            }
            effect.radius = (float) (radiusPerTick * currentIteration[0]);
        }, 0, 1);

        effect.start();
    }

    @Override
    public void playCubeEffect(Location location, double size, Particle particle, int durationTicks) {
        CubeEffect effect = new CubeEffect(effectManager);
        effect.setLocation(location);
        effect.particle = particle;
        effect.edgeLength = (float) size;
        effect.particles = 8;
        effect.iterations = durationTicks;
        effect.period = 1;
        effectManager.start(effect);
    }

    @Override
    public void playTrailEffect(UUID entityId, Particle particle, int durationTicks) {
        Entity entity = getEntity(entityId);
        if (entity == null) return;

        // Manual trail implementation since TrailEffect might not exist
        final int[] ticksRemaining = {durationTicks};

        scheduleRepeating(() -> {
            if (ticksRemaining[0]-- <= 0 || !entity.isValid()) {
                return;
            }

            Location loc = entity.getLocation().add(0, 0.5, 0);
            world.spawnParticle(particle, loc, 3, 0.2, 0.2, 0.2, 0);
        }, 0, 2); // Every 2 ticks for performance
    }

    @Override
    public void playBeamEffect(Location start, Location end, Particle particle,
                               double width, int durationTicks) {
        // Use modified line effect with thickness
        LineEffect effect = new LineEffect(effectManager);
        effect.setLocation(start);
        effect.setTarget(end);
        effect.particle = particle;
        effect.particles = (int) (start.distance(end) * 5); // Dense particles
        effect.iterations = durationTicks;
        effect.period = 1;
        effectManager.start(effect);
    }

    @Override
    public void playExplosionRingEffect(Location center, double radius, Particle particle) {
        final double[] currentRadius = {0.1};
        final double radiusStep = radius / 20;

        scheduleRepeating(new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 20) {
                    return;
                }

                // Draw circle at current radius
                for (int i = 0; i < 50; i++) {
                    double angle = 2 * Math.PI * i / 50;
                    double x = currentRadius[0] * Math.cos(angle);
                    double z = currentRadius[0] * Math.sin(angle);

                    Location particleLoc = center.clone().add(x, 0, z);
                    world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
                }

                currentRadius[0] += radiusStep;
            }
        }, 0, 1);
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