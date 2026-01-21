package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
        EXILE("Вигнання", "Відкидає всіх ворогів"),
        RESTRICTION("Обмеження", "Створює в'язницю"),
        IMPRISONMENT("Ув'язнення", "Зупиняє рух цілі"),
        DEATH("Смерть", "Наносить смертельний урон"),
        EVAPORATION("Випарення", "Удар блискавки");

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

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
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
        boolean isSneaking = context.playerData().isSneaking(casterId);

        // 1. Переключення режиму (Shift + PKM)
        if (isSneaking) {
            return switchMode(context, casterId);
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

    private AbilityResult switchMode(IAbilityContext context, UUID casterId) {
        VerdictMode currentMode = getCurrentMode(casterId);
        VerdictMode newMode = currentMode.next();
        playerModes.put(casterId, newMode);

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("⚖ Режим Вердикту: ", NamedTextColor.GOLD)
                        .append(Component.text(newMode.displayName + " - " + newMode.description, NamedTextColor.YELLOW))
        );

        Location casterLoc = context.getCasterLocation();
        context.effects().spawnParticle(Particle.ENCHANT, casterLoc.add(0, 1, 0), 20, 0.3, 0.5, 0.3);

        return AbilityResult.deferred();
    }

    private AbilityResult executeExile(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Location center = context.getCasterLocation();

        List<Player> targets = context.targeting().getNearbyPlayers(EXILE_RADIUS).stream()
                .filter(p -> !p.getUniqueId().equals(casterId))
                .toList();

        if (targets.isEmpty()) return AbilityResult.failure("Немає цілей поблизу!");

        for (Player target : targets) {
            exilePlayer(context, target, center);
        }

        showExileEffects(context, center);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("⚖ Вигнання виконано! Відкинуто гравців: " + targets.size(), NamedTextColor.GOLD)
        );

        return AbilityResult.success();
    }

    private void exilePlayer(IAbilityContext context, Player target, Location center) {
        UUID targetId = target.getUniqueId();

        Vector direction = target.getLocation().toVector().subtract(center.toVector()).normalize();
        Vector velocity = direction.multiply(EXILE_DISTANCE / 10.0);
        velocity.setY(1.5);

        context.entity().setVelocity(targetId, velocity);
        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOW_FALLING, 100, 0);

        context.messaging().sendMessageToActionBar(
                targetId,
                Component.text("⚖ Ви були вигнані!", NamedTextColor.GOLD)
        );

        Location targetLoc = context.playerData().getCurrentLocation(targetId);
        if (targetLoc != null) {
            context.effects().playSound(targetLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
        }
    }

    private AbilityResult executeRestriction(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Location center = context.getCasterLocation();

        RestrictionZone zone = new RestrictionZone(center.clone(), RESTRICTION_SIZE, casterId);
        activeRestrictions.put(casterId, zone);

        List<Player> trapped = context.targeting().getNearbyPlayers(RESTRICTION_SIZE).stream()
                .filter(p -> !p.getUniqueId().equals(casterId))
                .filter(p -> zone.isInside(p.getLocation()))
                .toList();

        startRestrictionMonitoring(context, zone, trapped);
        showRestrictionEffects(context, zone);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("⚖ Зону обмеження створено! Захоплено: " + trapped.size(), NamedTextColor.LIGHT_PURPLE)
        );

        return AbilityResult.success();
    }

    private void startRestrictionMonitoring(IAbilityContext context, RestrictionZone zone, List<Player> trapped) {
        UUID casterId = context.getCasterId();
        Set<UUID> trappedIds = trapped.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());

        context.events().subscribeToTemporaryEvent(
                casterId,
                PlayerMoveEvent.class,
                event -> {
                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();
                    Location to = event.getTo();

                    return to != null
                            && !playerId.equals(casterId)
                            && trappedIds.contains(playerId)
                            && !zone.isInside(to);
                },
                event -> {
                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();
                    Location to = event.getTo();
                    if (to == null) return;

                    event.setCancelled(true);
                    Vector push = zone.getCenter().toVector().subtract(to.toVector()).normalize().multiply(0.5);
                    context.entity().setVelocity(playerId, push);

                    context.effects().playSoundForPlayer(playerId, Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);

                    context.messaging().sendMessageToActionBar(
                            playerId,
                            Component.text("⛔ Ви не можете покинути зону обмеження!", NamedTextColor.RED)
                    );
                },
                RESTRICTION_DURATION_TICKS
        );

        context.scheduling().scheduleDelayed(() -> {
            activeRestrictions.remove(casterId);

            if (context.playerData().isOnline(casterId)) {
                context.messaging().sendMessageToActionBar(
                        casterId,
                        Component.text("⚖ Зона обмеження зникла.", NamedTextColor.GRAY)
                );
            }

            context.effects().spawnParticle(Particle.PORTAL, zone.getCenter(), 100, 5, 5, 5);

            for (UUID trappedId : trappedIds) {
                if (context.playerData().isOnline(trappedId)) {
                    context.messaging().sendMessageToActionBar(
                            trappedId,
                            Component.text("🔓 Ви звільнені!", NamedTextColor.GREEN)
                    );
                }
            }
        }, RESTRICTION_DURATION_TICKS);

        // Візуальні границі
        for (int i = 0; i < RESTRICTION_DURATION_TICKS / 20; i++) {
            context.scheduling().scheduleDelayed(() -> {
                if (activeRestrictions.containsKey(casterId)) {
                    showRestrictionBoundary(context, zone);
                }
            }, i * 20L);
        }
    }

    private AbilityResult executeImprisonment(IAbilityContext context) {
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(30);
        if (targetOpt.isEmpty()) return AbilityResult.invalidTarget("Наведіть на гравця!");

        Player target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        imprisonPlayer(context, targetId);

        Location targetLoc = context.playerData().getCurrentLocation(targetId);
        if (targetLoc != null) {
            showImprisonmentEffects(context, targetLoc);
        }

        context.messaging().sendMessageToActionBar(
                targetId,
                Component.text("⚖ Ви ув'язнені! (10 сек)", NamedTextColor.BLUE)
        );

        String targetName = context.playerData().getName(targetId);
        context.messaging().sendMessageToActionBar(
                context.getCasterId(),
                Component.text("⚖ Гравця " + targetName + " ув'язнено!", NamedTextColor.BLUE)
        );

        return AbilityResult.success();
    }

    private void imprisonPlayer(IAbilityContext context, UUID targetId) {
        Location originalLoc = context.playerData().getCurrentLocation(targetId);
        if (originalLoc == null) return;

        Location freezeLoc = originalLoc.clone();

        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, IMPRISONMENT_DURATION_TICKS, 255);
        context.entity().applyPotionEffect(targetId, PotionEffectType.JUMP_BOOST, IMPRISONMENT_DURATION_TICKS, 250);
        context.entity().applyPotionEffect(targetId, PotionEffectType.MINING_FATIGUE, IMPRISONMENT_DURATION_TICKS, 5);

        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                PlayerTeleportEvent.class,
                event -> event.getPlayer().getUniqueId().equals(targetId),
                event -> event.setCancelled(true),
                IMPRISONMENT_DURATION_TICKS
        );

        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                PlayerMoveEvent.class,
                event -> {
                    if (!event.getPlayer().getUniqueId().equals(targetId)) return false;
                    Location to = event.getTo();
                    return to != null && freezeLoc.distance(to) > 0.5;
                },
                event -> {
                    event.setCancelled(true);
                    event.getPlayer().teleport(freezeLoc);
                },
                IMPRISONMENT_DURATION_TICKS
        );

        context.scheduling().scheduleDelayed(() -> {
            if (context.playerData().isOnline(targetId)) {
                context.messaging().sendMessageToActionBar(
                        targetId,
                        Component.text("🔓 Ви звільнені!", NamedTextColor.GREEN)
                );

                Location loc = context.playerData().getCurrentLocation(targetId);
                if (loc != null) {
                    context.effects().playSound(loc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
                }
            }
        }, IMPRISONMENT_DURATION_TICKS);
    }

    private AbilityResult executeDeath(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(30);
        if (targetOpt.isEmpty()) return AbilityResult.invalidTarget("Наведіть на ціль!");

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        context.entity().damage(targetId, DEATH_DAMAGE);

        Location targetLoc = context.playerData().getCurrentLocation(targetId);
        if (targetLoc != null) {
            showDeathEffects(context, targetLoc);
        }

        if (target instanceof Player) {
            context.messaging().sendMessageToActionBar(
                    targetId,
                    Component.text("⚖ ВИРОК: СМЕРТЬ!", NamedTextColor.DARK_RED)
            );
        }

        context.messaging().sendMessageToActionBar(
                context.getCasterId(),
                Component.text("⚖ Вирок винесено: " + String.format("%.1f", DEATH_DAMAGE / 2) + " сердець урону", NamedTextColor.DARK_RED)
        );

        return AbilityResult.success();
    }

    private AbilityResult executeEvaporation(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(EVAPORATION_RADIUS);
        if (targetOpt.isEmpty()) return AbilityResult.invalidTarget("Наведіть на ціль!");

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();
        Location targetLoc = context.playerData().getCurrentLocation(targetId);

        if (targetLoc != null) {
            strikeWithLightning(context, targetId, targetLoc);
            showEvaporationEffects(context, targetLoc);
        }

        context.entity().damage(targetId, EVAPORATION_DAMAGE);

        if (target instanceof Player) {
            context.messaging().sendMessageToActionBar(
                    targetId,
                    Component.text("⚖ Небесна кара!", NamedTextColor.AQUA)
            );
        }

        context.messaging().sendMessageToActionBar(
                context.getCasterId(),
                Component.text("⚖ Блискавка вразила ціль!", NamedTextColor.AQUA)
        );

        return AbilityResult.success();
    }

    // ===========================================
    // UTILS & VISUALS
    // ===========================================

    private void showExileEffects(IAbilityContext context, Location center) {
        context.effects().playWaveEffect(center, EXILE_RADIUS, Particle.EXPLOSION, 10);
        context.effects().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

        for (int i = 0; i < 50; i++) {
            context.effects().spawnParticle(
                    Particle.END_ROD,
                    center.clone().add(0, i * 0.5, 0),
                    3,
                    0.2, 0.1, 0.2
            );
        }
    }

    private void showRestrictionEffects(IAbilityContext context, RestrictionZone zone) {
        Location center = zone.getCenter();
        showRestrictionBoundary(context, zone);
        context.effects().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
        context.effects().playVortexEffect(center, 10, zone.getSize() / 2.0, Particle.ENCHANT, 20);
    }

    private void showRestrictionBoundary(IAbilityContext context, RestrictionZone zone) {
        Location center = zone.getCenter();
        double size = zone.getSize() / 2.0;

        for (double y = 0; y < 10; y += 0.5) {
            context.effects().spawnParticle(Particle.WITCH, center.clone().add(size, y, size), 1);
            context.effects().spawnParticle(Particle.WITCH, center.clone().add(-size, y, size), 1);
            context.effects().spawnParticle(Particle.WITCH, center.clone().add(size, y, -size), 1);
            context.effects().spawnParticle(Particle.WITCH, center.clone().add(-size, y, -size), 1);
        }
    }

    private void showImprisonmentEffects(IAbilityContext context, Location loc) {
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 2 * i / 4;
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;
            context.effects().playLineEffect(loc.clone().add(x, 3, z), loc.clone().add(0, 1, 0), Particle.CRIT);
        }
        context.effects().playSound(loc, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.5f);
        context.effects().playSound(loc, Sound.BLOCK_IRON_DOOR_CLOSE, 1.5f, 0.8f);
    }

    private void showDeathEffects(IAbilityContext context, Location loc) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.5f);

        context.effects().spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1, 0), 100, 0.5, 1, 0.5);
        context.effects().spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 50, 0.3, 0.8, 0.3);
        context.effects().playSound(loc, Sound.ENTITY_WITHER_HURT, 2.0f, 0.5f);
        context.effects().playSound(loc, Sound.BLOCK_BELL_USE, 2.0f, 0.5f);
        context.effects().playExplosionRingEffect(loc, 3, Particle.DUST, dustOptions);
    }

    private void strikeWithLightning(IAbilityContext context, UUID targetId, Location loc) {
        // Візуальний ефект блискавки
        loc.getWorld().strikeLightningEffect(loc);
        context.entity().applyPotionEffect(targetId, PotionEffectType.BLINDNESS, 60, 0);
    }

    private void showEvaporationEffects(IAbilityContext context, Location loc) {
        context.effects().playWaveEffect(loc, 5, Particle.FLASH, 10);
        context.effects().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 100, 1, 1, 1);
        context.effects().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
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