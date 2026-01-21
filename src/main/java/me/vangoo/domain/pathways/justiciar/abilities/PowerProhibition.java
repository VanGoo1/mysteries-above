package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain Ability: Prohibition of Power (Заборона Сил)
 */
public class PowerProhibition extends ActiveAbility {

    private static final double RADIUS = 40.0;
    private static final int DURATION_SECONDS = 45;
    private static final int DURATION_TICKS = DURATION_SECONDS * 20;

    private static final Map<UUID, ProhibitionMode> playerModes = new ConcurrentHashMap<>();
    private static final Set<ProhibitionZone> activeZones = ConcurrentHashMap.newKeySet();
    // Додаємо карту для зберігання часу останнього перемикання
    private static final Map<UUID, Long> switchCooldowns = new ConcurrentHashMap<>();
    @Override
    public String getName() {
        return "Заборона";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Накладає заборону на обрану дію (впливає на всіх у зоні, включаючи кастера).\n" +
                "§7▪ Радіус: §f" + (int)RADIUS + " блоків\n" +
                "§7▪ Тривалість: §f" + DURATION_SECONDS + " секунд\n" +
                "§7▪ Shift+ПКМ: §fпереключити режим\n" +
                "§7▪ ПКМ: §fнакласти заборону\n" +
                "§eРежими заборон:\n" +
                "§7  1. Заборона телепортації\n" +
                "§7  2. Заборона вбивств\n" +
                "§7  3. Заборона входу в домен\n" +
                "§7  4. Заборона польоту\n" +
                "§7  5. Заборона використання зброї\n" +
                "§7  6. Блокування взаємодії";
    }

    @Override
    public int getSpiritualityCost() {
        return 250;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 75;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        boolean isSneaking = context.playerData().isSneaking(casterId);

        // 1. Shift - Перемикання режиму
        if (isSneaking) {
            return switchMode(context, casterId);
        }

        // 2. Активація заборони
        ProhibitionMode mode = playerModes.getOrDefault(casterId, ProhibitionMode.TELEPORT_BAN);
        Beyonder casterBeyonder = context.getCasterBeyonder();

        int casterSequence = casterBeyonder == null ? 9 : casterBeyonder.getSequenceLevel();
        double dynamicRadius = getJurisdictionRadius(casterSequence);

        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(dynamicRadius);

        // Ефекти активації
        showActivationEffect(context, mode, dynamicRadius);

        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        ProhibitionZone zone = new ProhibitionZone(
                context.getCasterLocation(),
                dynamicRadius,
                mode,
                casterSequence,
                casterId,
                System.currentTimeMillis() + (DURATION_SECONDS * 1000L)
        );

        // Початковий скан гравців
        processInitialTargets(context, nearbyPlayers, zone, casterId, casterSequence,
                affectedPlayers, resistedPlayers);

        activeZones.add(zone);
        subscribeToProhibitionEvents(context, zone);
        maintainZoneVisuals(context, zone);

        context.scheduling().scheduleDelayed(() -> {
            activeZones.remove(zone);
            announceZoneExpiration(context, zone);
        }, DURATION_TICKS);

        showResultStatistics(context, mode, affectedPlayers, resistedPlayers);

        return AbilityResult.success();
    }

    private AbilityResult switchMode(IAbilityContext context, UUID casterId) {
        // --- FIX START: Debounce (захист від подвійного кліку) ---
        long currentTime = System.currentTimeMillis();
        long lastTime = switchCooldowns.getOrDefault(casterId, 0L);

        // Якщо пройшло менше 300мс з минулого перемикання - ігноруємо
        if (currentTime - lastTime < 300) {
            return AbilityResult.deferred();
        }

        switchCooldowns.put(casterId, currentTime);
        // --- FIX END ---

        ProhibitionMode currentMode = playerModes.getOrDefault(casterId, ProhibitionMode.TELEPORT_BAN);
        ProhibitionMode[] modes = ProhibitionMode.values();
        int nextIndex = (currentMode.ordinal() + 1) % modes.length;
        ProhibitionMode newMode = modes[nextIndex];

        playerModes.put(casterId, newMode);

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("⚖ Режим: ", NamedTextColor.GOLD)
                        .append(Component.text(newMode.getDisplayName(), NamedTextColor.YELLOW))
        );

        Location casterLoc = context.getCasterLocation();
        // Піднімаємо партікли трохи вище, щоб не заважали огляду
        context.effects().spawnParticle(newMode.getParticle(), casterLoc.add(0, 1.5, 0), 30, 0.5, 0.5, 0.5);

        return AbilityResult.deferred();
    }

    private void processInitialTargets(IAbilityContext context, List<Player> nearbyPlayers,
                                       ProhibitionZone zone, UUID casterId, int casterSequence,
                                       Set<UUID> affectedPlayers, Set<UUID> resistedPlayers) {
        for (Player target : nearbyPlayers) {
            UUID targetId = target.getUniqueId();
            Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
            int targetSequence = targetBeyonder == null ? 9 : targetBeyonder.getSequenceLevel();

            if (targetId.equals(casterId)) {
                zone.addBannedPlayer(targetId);
                affectedPlayers.add(targetId);
                showProhibitionEffect(context, target, zone.mode);
                sendProhibitionMessage(context, targetId, zone.mode, true);
                continue;
            }

            // Перевірка резисту
            boolean isStronger = targetBeyonder != null && targetSequence < casterSequence;
            if (isStronger && Math.random() < 0.5) {
                resistedPlayers.add(targetId);
                showResistEffect(context, target);
                continue;
            }

            zone.addBannedPlayer(targetId);
            affectedPlayers.add(targetId);
            showProhibitionEffect(context, target, zone.mode);
            sendProhibitionMessage(context, targetId, zone.mode, false);
        }
    }

    private void subscribeToProhibitionEvents(IAbilityContext context, ProhibitionZone zone) {
        switch (zone.mode) {
            case TELEPORT_BAN -> subscribeTeleportBlocking(context, zone);
            case KILL_BAN -> subscribeKillBlocking(context, zone);
            case JURISDICTION_ENTRY_BAN -> subscribeEntryBlocking(context, zone);
            case FLIGHT_BAN -> subscribeFlightBlocking(context, zone);
            case WEAPON_USE_BAN -> subscribeWeaponUseBlocking(context, zone);
            case ENVIRONMENT_LOCK -> subscribeEnvironmentLock(context, zone);
        }
    }

    private void subscribeTeleportBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                PlayerTeleportEvent.class,
                event -> activeZones.contains(zone) &&
                        zone.isBanned(event.getPlayer().getUniqueId()) &&
                        zone.isInside(event.getPlayer().getLocation()),
                event -> {
                    event.setCancelled(true);
                    UUID playerId = event.getPlayer().getUniqueId();

                    context.messaging().sendMessageToActionBar(
                            playerId,
                            Component.text("⚖ Телепортація заборонена!", NamedTextColor.RED)
                    );

                    context.effects().playSoundForPlayer(playerId, Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);

                    notifyCasterOfViolation(context, zone, playerId, "спробував телепортуватися");

                    Location playerLoc = context.playerData().getCurrentLocation(playerId);
                    if (playerLoc != null) {
                        context.effects().spawnParticle(Particle.SMOKE, playerLoc.add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                    }
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeKillBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                EntityDamageByEntityEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;
                    if (!(event.getDamager() instanceof Player attacker)) return false;
                    if (!(event.getEntity() instanceof Player victim)) return false;

                    if (!zone.isInside(victim.getLocation())) return false;

                    boolean isAttackerBanned = zone.isBanned(attacker.getUniqueId());
                    boolean isAttackerCaster = attacker.getUniqueId().equals(zone.casterId);

                    if (!isAttackerBanned && !isAttackerCaster) return false;

                    double damageAfter = victim.getHealth() - event.getFinalDamage();
                    return damageAfter <= 0;
                },
                event -> {
                    event.setCancelled(true);

                    Player victim = (Player) event.getEntity();
                    Player attacker = (Player) event.getDamager();

                    UUID victimId = victim.getUniqueId();
                    UUID attackerId = attacker.getUniqueId();

                    // Емуляція Тотема Безсмертя
                    if (victim.getHealth() < 1.0) {
                        victim.setHealth(1.0);
                    }

                    victim.playEffect(EntityEffect.TOTEM_RESURRECT);

                    // Баффи після тотема
                    context.entity().applyPotionEffect(victimId, PotionEffectType.REGENERATION, 900, 1);
                    context.entity().applyPotionEffect(victimId, PotionEffectType.FIRE_RESISTANCE, 800, 0);
                    context.entity().applyPotionEffect(victimId, PotionEffectType.ABSORPTION, 100, 1);

                    // Сповіщення
                    context.messaging().sendMessageToActionBar(
                            attackerId,
                            Component.text("⚖ Вбивство заборонено! (Спрацював захист)", NamedTextColor.RED)
                    );

                    context.messaging().sendMessageToActionBar(
                            victimId,
                            Component.text("⚖ Заборона врятувала вас від смерті!", NamedTextColor.GREEN)
                    );

                    context.effects().playSoundForPlayer(attackerId, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

                    String victimName = context.playerData().getName(victimId);
                    notifyCasterOfViolation(context, zone, attackerId, "спробував вбити " + victimName);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeEntryBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                PlayerMoveEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;

                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();

                    // Імунітет Кастера
                    if (playerId.equals(zone.casterId)) return false;

                    Location to = event.getTo();
                    if (to == null || !zone.isInside(to)) return false;

                    // Перевірка рівня сил
                    Beyonder targetBeyonder = context.beyonder().getBeyonder(playerId);
                    if (targetBeyonder != null && targetBeyonder.getSequenceLevel() < zone.casterSequence) {
                        return false;
                    }

                    return true;
                },
                event -> {
                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();

                    // Вектор відштовхування
                    Location playerLoc = player.getLocation();
                    Location center = zone.center.clone();
                    Vector direction = playerLoc.toVector().subtract(center.toVector()).normalize();

                    if (Double.isNaN(direction.getX())) {
                        direction = new Vector(0, 0, 1);
                    }

                    // Відштовхуємо назад
                    context.entity().setVelocity(playerId, direction.multiply(1.2).setY(0.4));

                    context.messaging().sendMessageToActionBar(
                            playerId,
                            Component.text("⛔ Рівень заборони занадто високий для вас!", NamedTextColor.RED)
                    );

                    context.effects().playSoundForPlayer(playerId, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
                    context.effects().spawnParticle(Particle.SOUL_FIRE_FLAME, playerLoc.add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeFlightBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.scheduling().scheduleRepeating(() -> {
            if (!activeZones.contains(zone)) return;

            for (UUID playerId : zone.bannedPlayers) {
                if (!context.playerData().isOnline(playerId)) continue;

                Location playerLoc = context.playerData().getCurrentLocation(playerId);
                if (playerLoc == null || !zone.isInside(playerLoc)) continue;

                Player player = Bukkit.getPlayer(playerId);
                if (player == null) continue;

                if (player.isFlying() || player.getAllowFlight()) {
                    player.setFlying(false);
                    player.setAllowFlight(false);

                    context.messaging().sendMessageToActionBar(
                            playerId,
                            Component.text("⚖ Політ заборонено!", NamedTextColor.RED)
                    );

                    context.effects().playSoundForPlayer(playerId, Sound.ENTITY_BAT_DEATH, 1.0f, 0.8f);

                    notifyCasterOfViolation(context, zone, playerId, "спробував летіти");
                    context.effects().spawnParticle(Particle.CLOUD, playerLoc.add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                }
            }
        }, 0L, 5L);
    }

    private void subscribeWeaponUseBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                EntityDamageByEntityEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;
                    if (!(event.getDamager() instanceof Player attacker)) return false;
                    if (!zone.isInside(attacker.getLocation())) return false;

                    UUID attackerId = attacker.getUniqueId();
                    if (!zone.isBanned(attackerId) && !attackerId.equals(zone.casterId)) return false;

                    Material weapon = context.playerData().getMainHandItem(attackerId).getType();
                    return isBannedWeapon(weapon);
                },
                event -> {
                    event.setCancelled(true);
                    UUID attackerId = ((Player) event.getDamager()).getUniqueId();

                    context.messaging().sendMessageToActionBar(
                            attackerId,
                            Component.text("⚖ Використання зброї заборонено!", NamedTextColor.RED)
                    );

                    context.effects().playSoundForPlayer(attackerId, Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);

                    notifyCasterOfViolation(context, zone, attackerId, "спробував атакувати забороненою зброєю");

                    Location attackerLoc = context.playerData().getCurrentLocation(attackerId);
                    if (attackerLoc != null) {
                        context.effects().spawnParticle(Particle.SMOKE, attackerLoc.add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                    }
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeEnvironmentLock(IAbilityContext context, ProhibitionZone zone) {
        // Вибухи
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                ExplosionPrimeEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getEntity().getLocation()),
                ev -> {
                    ev.setCancelled(true);
                    context.effects().playSound(ev.getEntity().getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                },
                DURATION_TICKS + 20
        );

        // Спавн мобів
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                CreatureSpawnEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getLocation()),
                ev -> ev.setCancelled(true),
                DURATION_TICKS + 20
        );

        // Ламання блоків
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                BlockBreakEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getBlock().getLocation()),
                ev -> {
                    ev.setCancelled(true);
                    UUID playerId = ev.getPlayer().getUniqueId();

                    if (zone.isBanned(playerId)) {
                        context.messaging().sendMessageToActionBar(
                                playerId,
                                Component.text("⚖ Зміни блоків заборонені!", NamedTextColor.RED)
                        );
                        notifyCasterOfViolation(context, zone, playerId, "спробував зруйнувати блок");
                    }
                },
                DURATION_TICKS + 20
        );

        // Ставлення блоків
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                BlockPlaceEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getBlock().getLocation()),
                ev -> {
                    ev.setCancelled(true);
                    UUID playerId = ev.getPlayer().getUniqueId();

                    if (zone.isBanned(playerId)) {
                        context.messaging().sendMessageToActionBar(
                                playerId,
                                Component.text("⚖ Зміни блоків заборонені!", NamedTextColor.RED)
                        );
                        notifyCasterOfViolation(context, zone, playerId, "спробував поставити блок");
                    }
                },
                DURATION_TICKS + 20
        );

        // Погода
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                WeatherChangeEvent.class,
                ev -> {
                    if (!activeZones.contains(zone)) return false;
                    World w = (World) ev.getWorld();
                    return w.equals(zone.center.getWorld());
                },
                ev -> ev.setCancelled(true),
                DURATION_TICKS + 20
        );
    }

    private boolean isBannedWeapon(Material material) {
        String name = material.name();
        return name.contains("SWORD") || name.contains("AXE") ||
                name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT");
    }

    private void notifyCasterOfViolation(IAbilityContext context, ProhibitionZone zone, UUID violatorId, String action) {
        if (violatorId.equals(zone.casterId)) return;
        if (!context.playerData().isOnline(zone.casterId)) return;

        String violatorName = context.playerData().getName(violatorId);

        context.messaging().sendMessageToActionBar(
                zone.casterId,
                Component.text("⚖ ", NamedTextColor.GOLD)
                        .append(Component.text(violatorName, NamedTextColor.YELLOW))
                        .append(Component.text(" " + action, NamedTextColor.GRAY))
        );

        context.effects().playSoundForPlayer(zone.casterId, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f);

        Beyonder casterBeyonder = context.beyonder().getBeyonder(zone.casterId);
        if (casterBeyonder != null && casterBeyonder.getSequenceLevel() <= 5) {
            Punishment.registerViolation(context, zone.casterId, violatorId, action);
        }
    }

    private void announceZoneExpiration(IAbilityContext context, ProhibitionZone zone) {
        for (UUID playerId : zone.bannedPlayers) {
            if (!context.playerData().isOnline(playerId)) continue;

            context.messaging().sendMessageToActionBar(
                    playerId,
                    Component.text("⚖ Заборона знята.", NamedTextColor.GRAY)
            );

            Location playerLoc = context.playerData().getCurrentLocation(playerId);
            if (playerLoc != null) {
                context.effects().spawnParticle(Particle.END_ROD, playerLoc.add(0, 1, 0), 10, 0.3, 0.5, 0.3);
            }
        }
    }

    private void showActivationEffect(IAbilityContext context, ProhibitionMode mode, double radius) {
        Location loc = context.getCasterLocation();

        context.effects().playSphereEffect(loc.clone().add(0, 1, 0), radius, mode.getParticle(), 40);
        context.effects().playWaveEffect(loc.clone(), radius, Particle.CRIT, 30);
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);

        context.messaging().sendMessageToActionBar(
                context.getCasterId(),
                Component.text("⚖ Активовано заборону: ", NamedTextColor.GOLD)
                        .append(Component.text(mode.getDisplayName(), NamedTextColor.YELLOW))
        );
    }

    private void showProhibitionEffect(IAbilityContext context, Player target, ProhibitionMode mode) {
        Location loc = target.getLocation().add(0, 1, 0);
        context.effects().spawnParticle(mode.getParticle(), loc, 30, 0.5, 0.5, 0.5);
        context.effects().spawnParticle(Particle.CRIT, loc, 20, 0.3, 0.5, 0.3);
        context.effects().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f);
    }

    private void showResistEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);
        context.effects().spawnParticle(Particle.ENCHANT, loc, 50, 0.5, 0.5, 0.5);
        context.effects().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
    }

    private void sendProhibitionMessage(IAbilityContext context, UUID targetId, ProhibitionMode mode, boolean isCaster) {
        Component message = isCaster ?
                Component.text("⚖ Ви наклали заборону: ", NamedTextColor.GOLD)
                        .append(Component.text(mode.getDisplayName(), NamedTextColor.YELLOW)) :
                Component.text("⚖ На вас накладено заборону: ", NamedTextColor.RED)
                        .append(Component.text(mode.getDisplayName(), NamedTextColor.YELLOW));

        context.messaging().sendMessageToActionBar(targetId, message);
    }

    private void showResultStatistics(IAbilityContext context, ProhibitionMode mode, Set<UUID> affected, Set<UUID> resisted) {
        int total = affected.size() + resisted.size();
        if (total == 0) {
            context.messaging().sendMessage(context.getCasterId(), "§eУ зоні дії немає цілей");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("§6═══ Результат Заборони ═══\n");
        if (!affected.isEmpty()) {
            message.append("§c✓ Під забороною: §f").append(affected.size()).append("\n");
        }
        if (!resisted.isEmpty()) {
            message.append("§a✗ Опиралися: §f").append(resisted.size()).append("\n");
        }
        int successRate = total == 0 ? 0 : (int) ((affected.size() * 100.0) / total);
        message.append("§7Успішність: §e").append(successRate).append("%");

        context.messaging().sendMessage(context.getCasterId(), message.toString());
    }

    private void maintainZoneVisuals(IAbilityContext context, ProhibitionZone zone) {
        context.scheduling().scheduleRepeating(() -> {
            if (!activeZones.contains(zone)) return;
            drawZoneBoundary(context, zone);
        }, 0L, 30L);
    }

    private void drawZoneBoundary(IAbilityContext context, ProhibitionZone zone) {
        Location center = zone.center;
        double radius = zone.radius;

        for (int angle = 0; angle < 360; angle += 20) {
            double radians = Math.toRadians(angle);
            double x = center.getX() + radius * Math.cos(radians);
            double z = center.getZ() + radius * Math.sin(radians);
            Location particleLoc = new Location(center.getWorld(), x, center.getY(), z);
            context.effects().spawnParticle(zone.mode.getParticle(), particleLoc, 1, 0.0, 0.0, 0.0);
        }
    }

    private double getJurisdictionRadius(int sequence) {
        return switch (sequence) {
            case 6 -> 60.0;
            case 5 -> 100.0;
            default -> sequence < 5 ? 150.0 : 15.0;
        };
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
        activeZones.clear();
        switchCooldowns.clear(); // Очищаємо кулдауни
    }

    public enum ProhibitionMode {
        TELEPORT_BAN("Заборона телепортації", "Телепортація в зоні неможлива", Particle.ENCHANT),
        KILL_BAN("Заборона вбивств", "Вбивство гравців неможливе", Particle.ENCHANT),
        JURISDICTION_ENTRY_BAN("Заборона входу в Сферу Юрисдикції", "Вхід у чужий домен заборонено", Particle.ENCHANT),
        FLIGHT_BAN("Заборона польоту", "Політ в зоні неможливий", Particle.ENCHANT),
        WEAPON_USE_BAN("Заборона зброї", "Використання зброї заблоковано", Particle.ENCHANT),
        ENVIRONMENT_LOCK("Блокування взаємодії", "Зміни блоків заблоковані", Particle.ENCHANT);

        private final String displayName;
        private final String description;
        private final Particle particle;

        ProhibitionMode(String displayName, String description, Particle particle) {
            this.displayName = displayName;
            this.description = description;
            this.particle = particle;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Particle getParticle() { return particle; }
    }

    public static class ProhibitionZone {
        final Location center;
        final double radius;
        final ProhibitionMode mode;
        final int casterSequence;
        final UUID casterId;
        final long expirationTime;
        final Set<UUID> bannedPlayers;

        public ProhibitionZone(Location center, double radius, ProhibitionMode mode, int casterSequence, UUID casterId, long expirationTime) {
            this.center = center;
            this.radius = radius;
            this.mode = mode;
            this.casterSequence = casterSequence;
            this.casterId = casterId;
            this.expirationTime = expirationTime;
            this.bannedPlayers = ConcurrentHashMap.newKeySet();
        }

        public void addBannedPlayer(UUID playerId) { bannedPlayers.add(playerId); }
        public boolean isBanned(UUID playerId) { return bannedPlayers.contains(playerId); }
        public boolean isInside(Location loc) {
            if (!loc.getWorld().equals(center.getWorld())) return false;
            return loc.distance(center) <= radius;
        }
        public boolean isExpired() { return System.currentTimeMillis() > expirationTime; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProhibitionZone that)) return false;
            return Objects.equals(center, that.center) && expirationTime == that.expirationTime;
        }

        @Override
        public int hashCode() { return Objects.hash(center, expirationTime); }
    }
}