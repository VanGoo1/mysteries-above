package me.vangoo.application.services;


import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.*;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.function.Consumer;


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

    @Override
    public boolean isSneaking(UUID targetId) {
        Entity entity = getEntity(targetId);
        return entity instanceof Player p && p.isSneaking();
    }

    @Override
    public boolean hasItem(Material material, int amount) {
        return caster.getInventory().contains(material, amount);
    }

    @Override
    public void consumeItem(Material material, int amount) {
        if (!hasItem(material, amount)) return;

        for (ItemStack item : caster.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                int newAmount = item.getAmount() - amount;
                if (newAmount > 0) item.setAmount(newAmount);
                else caster.getInventory().remove(item);
                break;
            }
        }
    }

    @Override
    public Map<String, String> getTargetAnalysis(UUID targetId) {
        Entity entity = getEntity(targetId);
        if (!(entity instanceof Player target)) {
            return Map.of("Error", "Not a player");
        }

        // 1. Статистика
        int kills = target.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = target.getStatistic(Statistic.DEATHS);
        int mobKills = target.getStatistic(Statistic.MOB_KILLS);
        long hours = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60 / 60;

        // 2. Зброя в руках
        ItemStack handItem = target.getInventory().getItemInMainHand();
        String weaponName = "Нічого/Кулаки";
        if (handItem.getType() != Material.AIR) {
            if (handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()) {
                weaponName = handItem.getItemMeta().getDisplayName();
            } else {
                weaponName = handItem.getType().name().replace("_", " ").toLowerCase();
            }
        }

        // 3. Формуємо Map
        Map<String, String> data = new HashMap<>();
        data.put("Kills", String.valueOf(kills));
        data.put("Deaths", String.valueOf(deaths));
        data.put("MobKills", String.valueOf(mobKills));
        data.put("Hours", String.valueOf(hours));
        data.put("Weapon", weaponName);

        return data;
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

    @Override
    public Location getBedSpawnLocation(UUID targetId) {
        Entity entity = getEntity(targetId);
        if (entity instanceof Player p) {
            return p.getBedSpawnLocation();
        }
        return null;
    }

    @Override
    public long getPlayTimeHours(UUID targetId) {
        Entity entity = getEntity(targetId);
        if (entity instanceof Player p) {
            // PLAY_ONE_MINUTE це тіки (1/20 сек)
            return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60 / 60;
        }
        return 0;
    }

    @Override
    public String getMainHandItemName(UUID targetId) {
        Entity entity = getEntity(targetId);
        if (entity instanceof Player p) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) return "Нічого";

            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
            return item.getType().name().replace("_", " ").toLowerCase();
        }
        return "Невідомо";
    }

    @Override
    public int getDeathsStatistic(UUID targetId) {
        Entity entity = getEntity(targetId);
        return (entity instanceof Player p) ? p.getStatistic(Statistic.DEATHS) : 0;
    }

    @Override
    public List<String> getEnderChestContents(UUID targetId, int limit) {
        Player player = Bukkit.getPlayer(targetId);
        if (player == null) return Collections.emptyList();

        // Використовуємо Map для об'єднання однакових предметів
        // Ключ: Назва предмета, Значення: Загальна кількість
        Map<String, Integer> mergedItems = new HashMap<>();

        for (ItemStack item : player.getEnderChest().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                String name = formatMaterialName(item.getType());
                // Додаємо кількість до існуючої або створюємо нову
                mergedItems.put(name, mergedItems.getOrDefault(name, 0) + item.getAmount());
            }
        }

        // Перетворюємо Map назад у список рядків "Назва xКількість"
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mergedItems.entrySet()) {
            result.add(ChatColor.AQUA + entry.getKey() + ChatColor.WHITE + " x" + entry.getValue());
        }

        // Якщо ліміт вказаний і він менший за розмір списку, обрізаємо
        // (Але для Телепатії ми передаємо великий ліміт, тому покаже все)
        if (limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
    @Override
    public int getPlayerKills(UUID targetId) {
        Entity entity = getEntity(targetId);
        return (entity instanceof Player p) ? p.getStatistic(Statistic.PLAYER_KILLS) : 0;
    }

    @Override
    public int getVillagerKills(UUID targetId) {
        Entity entity = getEntity(targetId);
        return (entity instanceof Player p) ? p.getStatistic(Statistic.KILL_ENTITY, EntityType.VILLAGER) : 0;
    }

    @Override
    public Location getLastDeathLocation(UUID targetId) {
        Entity entity = getEntity(targetId);
        return (entity instanceof Player p) ? p.getLastDeathLocation() : null;
    }

    @Override
    public int getExperienceLevel(UUID targetId) {
        Entity entity = getEntity(targetId);
        return (entity instanceof Player p) ? p.getLevel() : 0;
    }

    @Override
    public int getBeyonderMastery(UUID targetId) {
        Beyonder b = beyonderService.getBeyonder(targetId);
        return (b != null) ? b.getMasteryValue() : 0;
    }

    @Override
    public void monitorSneaking(UUID targetId, int durationTicks, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            int currentTick = 0;

            @Override
            public void run() {
                Player player = Bukkit.getPlayer(targetId);

                // Якщо гравець вийшов з гри - вважаємо це відмовою
                if (player == null || !player.isOnline()) {
                    callback.accept(false);
                    this.cancel();
                    return;
                }

                // ПЕРЕВІРКА: Чи гравець присів?
                if (player.isSneaking()) {
                    callback.accept(true); // Успіх!
                    this.cancel();
                    return;
                }

                currentTick += 5; // Ми перевіряємо кожні 5 тіків
                if (currentTick >= durationTicks) {
                    callback.accept(false); // Час вийшов - відмова
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Запуск таймера: перевірка кожні 5 тіків
    }
    @Override
    public String analyzeGreed(UUID targetId) {
        Entity entity = getEntity(targetId);
        if (!(entity instanceof Player p)) return null;

        // Структура для аналізу ресурсів
        class ResourceData {
            String name;
            ChatColor color;
            int mined;
            int used;
            double value; // базова "цінність" для порівняння

            ResourceData(String name, ChatColor color, Material oreType, List<Material> usageItems, double value) {
                this.name = name;
                this.color = color;
                this.value = value;

                // Підраховуємо видобуто
                try {
                    this.mined = p.getStatistic(Statistic.MINE_BLOCK, oreType);
                } catch (Exception e) {
                    this.mined = 0;
                }

                // Підраховуємо витрачено (сума використання всіх предметів)
                this.used = 0;
                for (Material item : usageItems) {
                    try {
                        this.used += p.getStatistic(Statistic.USE_ITEM, item);
                    } catch (Exception e) {
                        // Ігноруємо помилки
                    }
                }
            }

            int getBalance() {
                return mined - used;
            }

            double getHoardingScore() {
                if (mined == 0) return 0;
                // Чим більший баланс відносно видобутку - тим більше "жадібність"
                return (double) getBalance() / mined * value;
            }
        }

        // Визначаємо всі ресурси для аналізу
        List<ResourceData> resources = new ArrayList<>();

        // Незерит (найцінніший)
        resources.add(new ResourceData(
                "Незерит",
                ChatColor.DARK_PURPLE,
                Material.ANCIENT_DEBRIS,
                Arrays.asList(
                        Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE,
                        Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL,
                        Material.NETHERITE_HOE, Material.NETHERITE_HELMET,
                        Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS,
                        Material.NETHERITE_BOOTS, Material.NETHERITE_BLOCK
                ),
                10.0
        ));

        // Алмази
        resources.add(new ResourceData(
                "Алмази",
                ChatColor.AQUA,
                Material.DIAMOND_ORE,
                Arrays.asList(
                        Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE,
                        Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL,
                        Material.DIAMOND_HOE, Material.DIAMOND_HELMET,
                        Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
                        Material.DIAMOND_BOOTS, Material.DIAMOND_BLOCK,
                        Material.ENCHANTING_TABLE, Material.JUKEBOX
                ),
                5.0
        ));

        // Емеральди
        resources.add(new ResourceData(
                "Емеральди",
                ChatColor.GREEN,
                Material.EMERALD_ORE,
                Arrays.asList(Material.EMERALD_BLOCK),
                7.0
        ));

        // Золото
        resources.add(new ResourceData(
                "Золото",
                ChatColor.GOLD,
                Material.GOLD_ORE,
                Arrays.asList(
                        Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE,
                        Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL,
                        Material.GOLDEN_HOE, Material.GOLDEN_HELMET,
                        Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS,
                        Material.GOLDEN_BOOTS, Material.GOLD_BLOCK,
                        Material.GOLDEN_APPLE, Material.CLOCK,
                        Material.POWERED_RAIL
                ),
                3.0
        ));

        // Залізо
        resources.add(new ResourceData(
                "Залізо",
                ChatColor.WHITE,
                Material.IRON_ORE,
                Arrays.asList(
                        Material.IRON_SWORD, Material.IRON_PICKAXE,
                        Material.IRON_AXE, Material.IRON_SHOVEL,
                        Material.IRON_HOE, Material.IRON_HELMET,
                        Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS,
                        Material.IRON_BOOTS, Material.IRON_BLOCK,
                        Material.BUCKET, Material.SHEARS,
                        Material.FLINT_AND_STEEL, Material.IRON_DOOR,
                        Material.IRON_TRAPDOOR, Material.CAULDRON,
                        Material.HOPPER, Material.MINECART,
                        Material.RAIL, Material.ANVIL
                ),
                1.0
        ));

        // Фільтруємо ресурси з нульовим видобутком
        resources.removeIf(r -> r.mined == 0);

        if (resources.isEmpty()) {
            return null;
        }

        // Знаходимо ресурс з найвищим показником накопичення
        ResourceData mostHoarded = resources.stream()
                .max(Comparator.comparingDouble(ResourceData::getHoardingScore))
                .orElse(null);

        if (mostHoarded == null) {
            return null;
        }

        // Формуємо повідомлення на основі балансу
        int balance = mostHoarded.getBalance();
        String behavior;
        ChatColor behaviorColor;

        if (balance > mostHoarded.mined * 0.7) {
            // Більше 70% накопичено
            behavior = "Скнара";
            behaviorColor = ChatColor.DARK_RED;
        } else if (balance > mostHoarded.mined * 0.3) {
            // 30-70% накопичено
            behavior = "Економний";
            behaviorColor = ChatColor.YELLOW;
        } else if (balance >= 0) {
            // Баланс позитивний але малий
            behavior = "Раціональний";
            behaviorColor = ChatColor.GREEN;
        } else {
            // Від'ємний баланс (витратив більше ніж видобув - торгівля?)
            behavior = "Марнотратний";
            behaviorColor = ChatColor.RED;
        }

        return ChatColor.GOLD + "💰 Економічний профіль: " + behaviorColor + behavior +
                ChatColor.GRAY + "\n   └─ " + mostHoarded.color + mostHoarded.name +
                ChatColor.GRAY + ": знайдено " + ChatColor.WHITE + mostHoarded.mined +
                ChatColor.GRAY + ", витрачено " + ChatColor.WHITE + mostHoarded.used +
                ChatColor.GRAY + " (баланс: " + (balance >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED) +
                balance + ChatColor.GRAY + ")";
    }

}