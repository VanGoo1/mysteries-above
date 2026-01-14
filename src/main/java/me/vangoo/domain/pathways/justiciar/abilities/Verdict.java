package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain: Verdict - Здібність виносити вирок
 * Послідовність 6 (Chaos Hunter)
 */
public class Verdict extends ActiveAbility {

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

    private static final Map<UUID, VerdictMode> playerModes = new ConcurrentHashMap<>();
    private static final Map<UUID, RestrictionZone> activeRestrictions = new ConcurrentHashMap<>();

    private static final int EXILE_RADIUS = 20;
    private static final int EXILE_DISTANCE = 50;
    private static final int RESTRICTION_SIZE = 10;
    private static final int RESTRICTION_DURATION_TICKS = 400;
    private static final int IMPRISONMENT_DURATION_TICKS = 200;
    private static final double DEATH_DAMAGE = 16.0;
    private static final int EVAPORATION_RADIUS = 20;
    private static final double EVAPORATION_DAMAGE = 10.0;

    @Override
    public String getName() {
        return "Вердикт";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return """
                §fВиносить вирок над ворогами.
                §7Shift + ПКМ: Переключити режим
                §7ПКМ: Виконати вирок""";
    }

    @Override
    public int getSpiritualityCost() {
        return 130;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 45;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCaster();

        // 1. Переключення режиму (Shift + PKM)
        if (caster != null && caster.isSneaking()) {
            VerdictMode currentMode = getCurrentMode(casterId);
            VerdictMode newMode = currentMode.next();
            playerModes.put(casterId, newMode);

            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);

            // ACTION BAR: Кастер (через контекст)
            context.sendMessageToActionBar(
                    Component.text("⚖ Режим Вердикту: ", NamedTextColor.GOLD)
                            .append(Component.text(newMode.displayName + " " + newMode.description))
            );

            context.spawnParticle(Particle.ENCHANT, caster.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            return AbilityResult.deferred();
        }

        // 2. Виконання здібності
        VerdictMode mode = getCurrentMode(casterId);
        return switch (mode) {
            case EXILE -> executeExile(context);
            case RESTRICTION -> executeRestriction(context);
            case IMPRISONMENT -> executeImprisonment(context);
            case DEATH -> executeDeath(context);
            case EVAPORATION -> executeEvaporation(context);
        };
    }

    // ===========================================
    // РЕЖИМИ
    // ===========================================

    private AbilityResult executeExile(IAbilityContext context) {
        Player caster = context.getCaster();
        Location center = caster.getLocation();

        List<Player> targets = context.getNearbyPlayers(EXILE_RADIUS).stream()
                .filter(p -> !p.equals(caster))
                .toList();

        if (targets.isEmpty()) return AbilityResult.failure("Немає цілей поблизу!");

        for (Player target : targets) {
            exilePlayer(context, target, center);
        }

        showExileEffects(context, center);

        // ACTION BAR: Кастер
        context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GOLD + "⚖ Вигнання виконано! Відкинуто гравців: " + targets.size()
        ));

        return AbilityResult.success();
    }

    private void exilePlayer(IAbilityContext context, Player target, Location center) {
        Vector direction = target.getLocation().toVector().subtract(center.toVector()).normalize();
        Vector velocity = direction.multiply(EXILE_DISTANCE / 10.0);
        velocity.setY(1.5);

        target.setVelocity(velocity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false));

        // ACTION BAR: Ціль (НОВИЙ МЕТОД КОНТЕКСТУ)
        context.sendMessageToActionBar(target, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GOLD + "⚖ Ви були вигнані!"
        ));

        target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
    }

    private AbilityResult executeRestriction(IAbilityContext context) {
        Player caster = context.getCaster();
        Location center = caster.getLocation();

        RestrictionZone zone = new RestrictionZone(center.clone(), RESTRICTION_SIZE, caster.getUniqueId());
        activeRestrictions.put(caster.getUniqueId(), zone);

        List<Player> trapped = context.getNearbyPlayers(RESTRICTION_SIZE).stream()
                .filter(p -> !p.equals(caster))
                .filter(p -> zone.isInside(p.getLocation()))
                .toList();

        startRestrictionMonitoring(context, zone, trapped);
        showRestrictionEffects(context, zone);

        // ACTION BAR: Кастер
        context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.LIGHT_PURPLE + "⚖ Зону обмеження створено! Захоплено: " + trapped.size()
        ));

        return AbilityResult.success();
    }

    private void startRestrictionMonitoring(IAbilityContext context, RestrictionZone zone, List<Player> trapped) {
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
                    Vector push = zone.getCenter().toVector().subtract(to.toVector()).normalize().multiply(0.5);
                    player.setVelocity(push);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);

                    // ACTION BAR: Ціль (НОВИЙ МЕТОД)
                    context.sendMessageToActionBar(player, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.RED + "⛔ Ви не можете покинути зону обмеження!"
                    ));
                },
                RESTRICTION_DURATION_TICKS
        );

        context.scheduleDelayed(() -> {
            activeRestrictions.remove(casterId);

            // ACTION BAR: Кастер
            Player caster = context.getCaster();
            if (caster != null && caster.isOnline()) {
                context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                        ChatColor.GRAY + "⚖ Зона обмеження зникла."
                ));
            }

            zone.getCenter().getWorld().spawnParticle(Particle.PORTAL, zone.getCenter(), 100, 5, 5, 5, 1);

            for (Player p : trapped) {
                if (p.isOnline()) {
                    // ACTION BAR: Ціль (НОВИЙ МЕТОД)
                    context.sendMessageToActionBar(p, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.GREEN + "🔓 Ви звільнені!"
                    ));
                }
            }
        }, RESTRICTION_DURATION_TICKS);

        for (int i = 0; i < RESTRICTION_DURATION_TICKS / 20; i++) {
            context.scheduleDelayed(() -> {
                if (activeRestrictions.containsKey(casterId)) showRestrictionBoundary(zone);
            }, i * 20L);
        }
    }

    private AbilityResult executeImprisonment(IAbilityContext context) {
        Optional<Player> targetOpt = context.getTargetedPlayer(30);
        if (targetOpt.isEmpty()) return AbilityResult.invalidTarget("Наведіть на гравця!");

        Player target = targetOpt.get();
        imprisonPlayer(context, target);
        showImprisonmentEffects(context, target.getLocation());

        // ACTION BAR: Ціль (НОВИЙ МЕТОД)
        context.sendMessageToActionBar(target, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.BLUE + "⚖ Ви ув'язнені! (10 сек)"
        ));

        // ACTION BAR: Кастер
        context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.BLUE + "⚖ Гравця " + target.getName() + " ув'язнено!"
        ));

        return AbilityResult.success();
    }

    private void imprisonPlayer(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        Location originalLoc = target.getLocation().clone();

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, IMPRISONMENT_DURATION_TICKS, 255, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, IMPRISONMENT_DURATION_TICKS, 250, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, IMPRISONMENT_DURATION_TICKS, 5, false, false));

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
                    event.setCancelled(true);
                    event.getPlayer().teleport(originalLoc);
                },
                IMPRISONMENT_DURATION_TICKS
        );

        context.scheduleDelayed(() -> {
            if (target.isOnline()) {
                // ACTION BAR: Ціль (НОВИЙ МЕТОД)
                context.sendMessageToActionBar(target, LegacyComponentSerializer.legacySection().deserialize(
                        ChatColor.GREEN + "🔓 Ви звільнені!"
                ));
                target.playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
            }
        }, IMPRISONMENT_DURATION_TICKS);
    }

    private AbilityResult executeDeath(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(30);
        if (targetOpt.isEmpty()) return AbilityResult.invalidTarget("Наведіть на ціль!");

        LivingEntity target = targetOpt.get();
        double newHealth = Math.max(0, target.getHealth() - DEATH_DAMAGE);
        target.setHealth(newHealth);

        showDeathEffects(context, target.getLocation());

        if (target instanceof Player p) {
            // ACTION BAR: Ціль (НОВИЙ МЕТОД)
            context.sendMessageToActionBar(p, LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.DARK_RED + "⚖ ВИРОК: СМЕРТЬ!"
            ));
        }

        // ACTION BAR: Кастер
        context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.DARK_RED + "⚖ Вирок винесено: " + String.format("%.1f", DEATH_DAMAGE / 2) + " сердець урону"
        ));

        return AbilityResult.success();
    }

    private AbilityResult executeEvaporation(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(EVAPORATION_RADIUS);
        if (targetOpt.isEmpty()) return AbilityResult.invalidTarget("Наведіть на ціль!");

        LivingEntity target = targetOpt.get();
        Location targetLoc = target.getLocation();

        strikeWithLightning(context, target, targetLoc);
        context.damage(target.getUniqueId(), EVAPORATION_DAMAGE);
        showEvaporationEffects(context, targetLoc);

        if (target instanceof Player p) {
            // ACTION BAR: Ціль (НОВИЙ МЕТОД)
            context.sendMessageToActionBar(p, LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.AQUA + "⚖ Небесна кара!"
            ));
        }

        // ACTION BAR: Кастер
        context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.AQUA + "⚖ Блискавка вразила ціль!"
        ));

        return AbilityResult.success();
    }

    // ===========================================
    // UTILS & VISUALS
    // ===========================================

    private void showExileEffects(IAbilityContext context, Location center) {
        World world = center.getWorld();
        context.playWaveEffect(center, EXILE_RADIUS, Particle.EXPLOSION, 10);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        for (int i = 0; i < 50; i++) {
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, i * 0.5, 0), 3, 0.2, 0.1, 0.2, 0);
        }
    }

    private void showRestrictionEffects(IAbilityContext context, RestrictionZone zone) {
        Location center = zone.getCenter();
        World world = center.getWorld();
        showRestrictionBoundary(zone);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
        context.playVortexEffect(center, 10, zone.getSize() / 2.0, Particle.ENCHANT, 20);
    }

    private void showRestrictionBoundary(RestrictionZone zone) {
        Location center = zone.getCenter();
        World world = center.getWorld();
        double size = zone.getSize() / 2.0;
        for (double y = 0; y < 10; y += 0.5) {
            world.spawnParticle(Particle.WITCH, center.clone().add(size, y, size), 1);
            world.spawnParticle(Particle.WITCH, center.clone().add(-size, y, size), 1);
            world.spawnParticle(Particle.WITCH, center.clone().add(size, y, -size), 1);
            world.spawnParticle(Particle.WITCH, center.clone().add(-size, y, -size), 1);
        }
    }

    private void showImprisonmentEffects(IAbilityContext context, Location loc) {
        World world = loc.getWorld();
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 2 * i / 4;
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;
            context.playLineEffect(loc.clone().add(x, 3, z), loc.clone().add(0, 1, 0), Particle.CRIT);
        }
        world.playSound(loc, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_IRON_DOOR_CLOSE, 1.5f, 0.8f);
    }

    private void showDeathEffects(IAbilityContext context, Location loc) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.5f);
        World world = loc.getWorld();
        world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.1);
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 50, 0.3, 0.8, 0.3, 0.05);
        world.playSound(loc, Sound.ENTITY_WITHER_HURT, 2.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_BELL_USE, 2.0f, 0.5f);
        context.playExplosionRingEffect(loc, 3, Particle.DUST, dustOptions);
    }

    private void strikeWithLightning(IAbilityContext context, LivingEntity target, Location loc) {
        World world = loc.getWorld();
        world.strikeLightningEffect(loc);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
    }

    private void showEvaporationEffects(IAbilityContext context, Location loc) {
        World world = loc.getWorld();
        context.playWaveEffect(loc, 5, Particle.FLASH, 10);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 100, 1, 1, 1, 0.2);
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
    }

    private VerdictMode getCurrentMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, VerdictMode.EXILE);
    }

    @Override
    public void cleanUp() {
        activeRestrictions.clear();
        playerModes.clear();
    }

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
            if (loc == null || center == null || !loc.getWorld().equals(center.getWorld())) return false;
            double halfSize = size / 2.0;
            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());
            double dy = Math.abs(loc.getY() - center.getY());
            return dx <= halfSize && dz <= halfSize && dy <= 10;
        }

        public Location getCenter() { return center.clone(); }
        public double getSize() { return size; }
    }
}