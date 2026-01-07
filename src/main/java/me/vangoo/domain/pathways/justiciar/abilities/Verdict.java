package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain: Verdict - Здібність виносити вирок
 *
 * Послідовність 6 (Chaos Hunter)
 *
 * Режими:
 * - Вигнання: Відкидає всіх гравців у радіусі 20 блоків на 50 блоків
 * - Обмеження: Створює невидиму кімнату 10x10, з якої не можуть вийти інші гравці 20 секунд
 * - Ув'язнення: Зупиняє цільового гравця на 10 секунд
 * - Смерть: Наносить 8 сердець чистого урону
 * - Випарення: Вдаряє блискавкою по цілі з радіусу 20 блоків, наносить 5 сердець
 *
 * Shift + ПКМ - переключення режиму (тут, всередині Verdict)
 * ПКМ - використання здібності (pipeline)
 */
public class Verdict extends ActiveAbility implements Listener {

    // Режими вироку
    public enum VerdictMode {
        EXILE("§6Вигнання", "§7Відкидає всіх ворогів"),
        RESTRICTION("§5Обмеження", "§7Створює в'язницю"),
        IMPRISONMENT("§9Ув'язнення", "§7Зупиняє рух цілі"),
        DEATH("§4Смерть", "§7Наносить смертельний урон"),
        EVAPORATION("§3Випарення", "§7Удар блискавки");

        private final String displayName;
        private final String description;

        VerdictMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public VerdictMode next() {
            VerdictMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    // Зберігання поточних режимів для кожного гравця
    private static final Map<UUID, VerdictMode> playerModes = new ConcurrentHashMap<>();

    // Зберігання активних обмежень
    private static final Map<UUID, RestrictionZone> activeRestrictions = new ConcurrentHashMap<>();

    // Маркер гравців, які щойно перемкнули режим (щоб pipeline не виконав дію)
    private final Set<UUID> recentlyCycled = ConcurrentHashMap.newKeySet();

    // Плагін (потрібен для планувальника)
    private Plugin plugin;

    // Конфігурація
    private static final int EXILE_RADIUS = 20;
    private static final int EXILE_DISTANCE = 50;
    private static final int RESTRICTION_SIZE = 10;
    private static final int RESTRICTION_DURATION_TICKS = 400; // 20 секунд
    private static final int IMPRISONMENT_DURATION_TICKS = 200; // 10 секунд
    private static final double DEATH_DAMAGE = 16.0; // 8 сердець
    private static final int EVAPORATION_RADIUS = 20;
    private static final double EVAPORATION_DAMAGE = 10.0; // 5 сердець

    // ---------- Публічні API ----------

    /**
     * Зареєструй цей об'єкт як слухача та збережи посилання на плагін.
     * Виклич у onEnable(): verdict.register(thisPluginInstance);
     */
    public void register(Plugin plugin) {
        if (plugin == null) return;
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "Вердикт";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return """
                §fВиносить вирок над ворогами.
                §7Shift + ПКМ: Переключити режим
                §7ПКМ: Виконати вирок
                §6Режими:
                §6• Вигнання §7- Відкинути всіх у радіусі
                §5• Обмеження §7- Створити в'язницю
                §9• Ув'язнення §7- Зупинити рух
                §4• Смерть §7- Смертельний урон
                §3• Випарення §7- Удар блискавки""";
    }
    @Override
    public int getSpiritualityCost() {
        return 130;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 45;
    }

    /**
     * Не блокуємо виконання тут — все обробляємо у performExecution і в нашому listener
     */
    @Override
    protected boolean canExecute(IAbilityContext context) {
        return true;
    }

    /**
     * Якщо гравець щойно перемикав режим (recentlyCycled) — нічого не виконуємо.
     * Інакше — виконуємо поточний режим.
     */
    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCaster();

        // ===============================
        // 🔁 ПЕРЕКЛЮЧЕННЯ РЕЖИМУ (НЕ КАСТ)
        // ===============================
        if (caster != null && caster.isSneaking()) {

            // захист від подвійного виклику
            if (!recentlyCycled.contains(casterId)) {
                recentlyCycled.add(casterId);

                if (plugin != null) {
                    plugin.getServer().getScheduler().runTaskLater(
                            plugin,
                            () -> recentlyCycled.remove(casterId),
                            6L
                    );
                }
            }

            VerdictMode newMode = cycleModeForPlayer(casterId, caster);

            // ❗ КЛЮЧОВИЙ МОМЕНТ:
            // success = false → НІ КУЛДАУНУ, НІ ВИТРАТ
            return AbilityResult.failure(
                    "⚖ Режим переключено: " +
                            newMode.displayName + " " + newMode.description
            );
        }

        // ===============================
        // ⚔ ЗВИЧАЙНЕ ВИКОНАННЯ ЗДІБНОСТІ
        // ===============================
        VerdictMode mode = getCurrentMode(casterId);

        return switch (mode) {
            case EXILE -> executeExile(context);
            case RESTRICTION -> executeRestriction(context);
            case IMPRISONMENT -> executeImprisonment(context);
            case DEATH -> executeDeath(context);
            case EVAPORATION -> executeEvaporation(context);
        };
    }



    // ===========================
    // Event handler (всередині Verdict)
    // ===========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Action action = event.getAction();

        if (!p.isSneaking()) return;
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Ставимо маркер одразу — щоб, якщо pipeline виконається майже одночасно,
        // performExecution бачив recentlyCycled і не виконував режим.
        UUID id = p.getUniqueId();
        recentlyCycled.add(id);

        // Переключаємо режим (візуали)
        cycleModeForPlayer(id, p);

        // Забираємо можливість подальшої обробки кліку
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setCancelled(true);

        // Прибираємо маркер через кілька тиків (6 — експериментально)
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> recentlyCycled.remove(id),
                    6L);
        } else {
            // запасний варіант, якщо plugin null (не рекомендовано)
            new Thread(() -> {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                recentlyCycled.remove(id);
            }).start();
        }
    }


    // ===========================================
    // РЕЖИМИ — без змін (твоя логіка)
    // ===========================================

    private AbilityResult executeExile(IAbilityContext context) {
        Player caster = context.getCaster();
        Location center = caster.getLocation();

        List<Player> targets = context.getNearbyPlayers(EXILE_RADIUS).stream()
                .filter(p -> !p.equals(caster))
                .toList();

        if (targets.isEmpty()) {
            return AbilityResult.failure("Немає цілей поблизу!");
        }

        for (Player target : targets) {
            exilePlayer(target, center);
        }

        showExileEffects(context, center);

        return AbilityResult.successWithMessage(
                ChatColor.GOLD + "⚖ Вигнання виконано! Відкинуто гравців: " + targets.size()
        );
    }

    private void exilePlayer(Player target, Location center) {
        Vector direction = target.getLocation().toVector()
                .subtract(center.toVector())
                .normalize();

        Vector velocity = direction.multiply(EXILE_DISTANCE / 10.0);
        velocity.setY(1.5);

        target.setVelocity(velocity);
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, 100, 0, false, false));

        target.sendMessage(ChatColor.GOLD + "⚖ Ви були вигнані!");
        target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
    }

    private void showExileEffects(IAbilityContext context, Location center) {
        World world = center.getWorld();
        context.playWaveEffect(center, EXILE_RADIUS, Particle.EXPLOSION, 10);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.8f);
        for (int i = 0; i < 50; i++) {
            world.spawnParticle(Particle.END_ROD,
                    center.clone().add(0, i * 0.5, 0),
                    3, 0.2, 0.1, 0.2, 0);
        }
    }

    private AbilityResult executeRestriction(IAbilityContext context) {
        Player caster = context.getCaster();
        Location center = caster.getLocation();

        RestrictionZone zone = new RestrictionZone(
                center.clone(),
                RESTRICTION_SIZE,
                caster.getUniqueId()
        );

        activeRestrictions.put(caster.getUniqueId(), zone);

        List<Player> trapped = context.getNearbyPlayers(RESTRICTION_SIZE).stream()
                .filter(p -> !p.equals(caster))
                .filter(p -> zone.isInside(p.getLocation()))
                .toList();

        startRestrictionMonitoring(context, zone, trapped);
        showRestrictionEffects(context, zone);

        caster.sendMessage(ChatColor.LIGHT_PURPLE +
                "⚖ Зону обмеження створено! Захоплено: " + trapped.size());

        return AbilityResult.success();
    }

    private void startRestrictionMonitoring(IAbilityContext context,
                                            RestrictionZone zone,
                                            List<Player> trapped) {
        UUID casterId = context.getCasterId();

        context.subscribeToEvent(
                PlayerMoveEvent.class,
                (PlayerMoveEvent event) -> {
                    Player player = event.getPlayer();
                    Location to = event.getTo();
                    return to != null
                            && !player.getUniqueId().equals(casterId)
                            && trapped.contains(player)
                            && !zone.isInside(to);
                },
                (PlayerMoveEvent event) -> {
                    Player player = event.getPlayer();
                    Location to = event.getTo();
                    if (to == null) return;

                    event.setCancelled(true);

                    Vector push = zone.getCenter().toVector()
                            .subtract(to.toVector())
                            .normalize()
                            .multiply(0.5);
                    player.setVelocity(push);

                    player.playSound(player.getLocation(),
                            Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
                },
                RESTRICTION_DURATION_TICKS
        );

        context.scheduleDelayed(() -> {
            activeRestrictions.remove(casterId);

            Player caster = context.getCaster();
            if (caster != null) {
                caster.sendMessage(ChatColor.GRAY + "Зона обмеження зникла.");
                zone.getCenter().getWorld().spawnParticle(
                        Particle.PORTAL,
                        zone.getCenter(),
                        100, 5, 5, 5, 1
                );
            }

            for (Player p : trapped) {
                if (p.isOnline()) {
                    p.sendMessage(ChatColor.GREEN + "Ви звільнені!");
                }
            }
        }, RESTRICTION_DURATION_TICKS);

        for (int i = 0; i < RESTRICTION_DURATION_TICKS / 20; i++) {
            int finalI = i;
            context.scheduleDelayed(() -> {
                if (activeRestrictions.containsKey(casterId)) {
                    showRestrictionBoundary(zone);
                }
            }, i * 20L);
        }
    }

    private void showRestrictionEffects(IAbilityContext context, RestrictionZone zone) {
        Location center = zone.getCenter();
        World world = center.getWorld();
        showRestrictionBoundary(zone);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.2f);
        context.playVortexEffect(center, 10, zone.getSize() / 2.0, Particle.ENCHANT, 20);
    }

    private void showRestrictionBoundary(RestrictionZone zone) {
        Location center = zone.getCenter();
        World world = center.getWorld();
        double size = zone.getSize() / 2.0;

        for (double y = 0; y < 10; y += 0.5) {
            world.spawnParticle(Particle.WITCH,
                    center.clone().add(size, y, size), 1);
            world.spawnParticle(Particle.WITCH,
                    center.clone().add(-size, y, size), 1);
            world.spawnParticle(Particle.WITCH,
                    center.clone().add(size, y, -size), 1);
            world.spawnParticle(Particle.WITCH,
                    center.clone().add(-size, y, -size), 1);
        }
    }

    private AbilityResult executeImprisonment(IAbilityContext context) {
        Optional<Player> targetOpt = context.getTargetedPlayer(30);

        if (targetOpt.isEmpty()) {
            return AbilityResult.invalidTarget("Наведіть на гравця!");
        }

        Player target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        imprisonPlayer(context, target);
        showImprisonmentEffects(context, target.getLocation());

        target.sendMessage(ChatColor.BLUE + "⚖ Ви ув'язнені! (10 сек)");

        return AbilityResult.successWithMessage(
                ChatColor.BLUE + "⚖ Гравця " + target.getName() + " ув'язнено!"
        );
    }

    private void imprisonPlayer(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        Location originalLoc = target.getLocation().clone();

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, IMPRISONMENT_DURATION_TICKS, 255,
                false, false));
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST, IMPRISONMENT_DURATION_TICKS, 250,
                false, false));
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.MINING_FATIGUE, IMPRISONMENT_DURATION_TICKS, 5,
                false, false));

        context.subscribeToEvent(
                PlayerTeleportEvent.class,
                (PlayerTeleportEvent event) -> event.getPlayer().getUniqueId().equals(targetId),
                (PlayerTeleportEvent event) -> event.setCancelled(true),
                IMPRISONMENT_DURATION_TICKS
        );

        context.subscribeToEvent(
                PlayerMoveEvent.class,
                (PlayerMoveEvent event) -> {
                    if (!event.getPlayer().getUniqueId().equals(targetId)) return false;
                    Location to = event.getTo();
                    return to != null && originalLoc.distance(to) > 0.5;
                },
                (PlayerMoveEvent event) -> {
                    Player p = event.getPlayer();
                    event.setCancelled(true);
                    p.teleport(originalLoc);
                },
                IMPRISONMENT_DURATION_TICKS
        );

        for (int i = 0; i < IMPRISONMENT_DURATION_TICKS / 10; i++) {
            context.scheduleDelayed(() -> {
                if (target.isOnline()) {
                    target.getWorld().spawnParticle(Particle.ENCHANT,
                            target.getLocation().add(0, 1, 0),
                            20, 0.3, 0.5, 0.3, 0);
                }
            }, i * 10L);
        }

        context.scheduleDelayed(() -> {
            if (target.isOnline()) {
                target.sendMessage(ChatColor.GREEN + "Ви звільнені!");
                target.playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
            }
        }, IMPRISONMENT_DURATION_TICKS);
    }

    private void showImprisonmentEffects(IAbilityContext context, Location loc) {
        World world = loc.getWorld();

        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 2 * i / 4;
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;

            Location start = loc.clone().add(x, 3, z);
            context.playLineEffect(start, loc.clone().add(0, 1, 0),
                    Particle.CRIT);
        }

        world.playSound(loc, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_IRON_DOOR_CLOSE, 1.5f, 0.8f);
    }

    private AbilityResult executeDeath(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(30);

        if (targetOpt.isEmpty()) {
            return AbilityResult.invalidTarget("Наведіть на ціль!");
        }

        LivingEntity target = targetOpt.get();
        double newHealth = Math.max(0, target.getHealth() - DEATH_DAMAGE);
        target.setHealth(newHealth);

        showDeathEffects(context, target.getLocation());

        if (target instanceof Player p) {
            p.sendMessage(ChatColor.DARK_RED + "⚖ ВИРОК: СМЕРТЬ!");
        }

        return AbilityResult.successWithMessage(
                ChatColor.DARK_RED + "⚖ Вирок винесено: " +
                        String.format("%.1f", DEATH_DAMAGE / 2) + " сердець урону"
        );
    }

    private void showDeathEffects(IAbilityContext context, Location loc) {
        World world = loc.getWorld();

        world.spawnParticle(Particle.SQUID_INK,
                loc.clone().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.1);
        world.spawnParticle(Particle.SOUL,
                loc.clone().add(0, 1, 0), 50, 0.3, 0.8, 0.3, 0.05);

        world.playSound(loc, Sound.ENTITY_WITHER_HURT, 2.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_PHANTOM_DEATH, 1.5f, 0.7f);
        world.playSound(loc, Sound.BLOCK_BELL_USE, 2.0f, 0.5f);

        context.playExplosionRingEffect(loc, 3, Particle.SMOKE);
    }

    private AbilityResult executeEvaporation(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(EVAPORATION_RADIUS);

        if (targetOpt.isEmpty()) {
            return AbilityResult.invalidTarget("Наведіть на ціль в радіусі 20 блоків!");
        }

        LivingEntity target = targetOpt.get();
        Location targetLoc = target.getLocation();

        strikeWithLightning(context, target, targetLoc);
        context.damage(target.getUniqueId(), EVAPORATION_DAMAGE);
        showEvaporationEffects(context, targetLoc);

        if (target instanceof Player p) {
            p.sendMessage(ChatColor.AQUA + "⚖ Небесна кара!");
        }

        return AbilityResult.successWithMessage(
                ChatColor.AQUA + "⚖ Блискавка вразила ціль!"
        );
    }

    private void strikeWithLightning(IAbilityContext context, LivingEntity target, Location loc) {
        World world = loc.getWorld();
        world.strikeLightningEffect(loc);

        for (int i = 0; i < 100; i++) {
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    loc.clone().add(0, i * 0.3, 0),
                    3, 0.1, 0.1, 0.1, 0);
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
    }

    private void showEvaporationEffects(IAbilityContext context, Location loc) {
        World world = loc.getWorld();
        context.playWaveEffect(loc, 5, Particle.FLASH, 10);

        world.spawnParticle(Particle.CLOUD,
                loc.clone().add(0, 1, 0), 100, 1, 1, 1, 0.2);
        world.spawnParticle(Particle.EXPLOSION,
                loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);

        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
    }

    // ===========================================
    // UTILITIES
    // ===========================================

    /**
     * Переключити режим для гравця та показати візуалізацію.
     * Викликається з onPlayerInteract.
     */
    public VerdictMode cycleModeForPlayer(UUID playerId, Player caster) {
        if (playerId == null || caster == null) return getCurrentMode(playerId);

        VerdictMode current = getCurrentMode(playerId);
        VerdictMode next = current.next();
        playerModes.put(playerId, next);

        try {
            caster.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GOLD + "⚖ Режим: " + next.displayName + " " + next.description)
            );

            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            caster.getWorld().spawnParticle(Particle.ENCHANT,
                    caster.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Verdict: cycle visuals failed: " + ex.getMessage());
        }

        return next;
    }

    private VerdictMode getCurrentMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, VerdictMode.EXILE);
    }

    @Override
    public void cleanUp() {
        activeRestrictions.clear();
        playerModes.clear();
        recentlyCycled.clear();
    }

    // ===========================================
    // HELPER CLASSES
    // ===========================================
    private static class RestrictionZone {
        private final Location center;
        private final double size;
        private final UUID casterId;

        public RestrictionZone(Location center, double size, UUID casterId) {
            this.center = center;
            this.size = size;
            this.casterId = casterId;
        }

        public boolean isInside(Location loc) {
            if (loc == null || center == null || !loc.getWorld().equals(center.getWorld())) {
                return false;
            }

            double halfSize = size / 2.0;
            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());
            double dy = Math.abs(loc.getY() - center.getY());

            return dx <= halfSize && dz <= halfSize && dy <= 10;
        }

        public Location getCenter() {
            return center.clone();
        }

        public double getSize() {
            return size;
        }
    }
}
