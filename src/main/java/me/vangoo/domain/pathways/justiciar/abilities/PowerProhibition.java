// UPDATED: PowerProhibition.java
package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.pathways.justiciar.abilities.AreaOfJurisdiction;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.World;
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

    // Нові структури для відслідковування вторгнень у домени
    // remaining seconds of grace for an intruder
    private static final Map<UUID, Integer> intrusionRemainingSeconds = new ConcurrentHashMap<>();
    // owner of the domain the player is currently intruding
    private static final Map<UUID, UUID> intrusionDomainOwner = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Заборона";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Накладає заборону на обрану дію (впливає на всіх у зоні, включаючи кастера).\n" +
                ChatColor.GRAY + "▪ Радіус: " + ChatColor.WHITE + (int)RADIUS + " блоків\n" +
                ChatColor.GRAY + "▪ Тривалість: " + ChatColor.WHITE + DURATION_SECONDS + " секунд\n" +
                ChatColor.GRAY + "▪ Shift+ПКМ: " + ChatColor.WHITE + "переключити режим\n" +
                ChatColor.GRAY + "▪ ПКМ: " + ChatColor.WHITE + "накласти заборону\n" +
                ChatColor.YELLOW + "Режими заборон:\n" +
                ChatColor.GRAY + "  1. Заборона телепортації\n" +
                ChatColor.GRAY + "  2. Заборона вбивств\n" +
                ChatColor.GRAY + "  3. Заборона входу в домен\n" +
                ChatColor.GRAY + "  4. Заборона польоту\n" +
                ChatColor.GRAY + "  5. Заборона використання зброї\n" +
                ChatColor.GRAY + "  6. Блокування середовища";
    }

    @Override
    public int getSpiritualityCost() {
        return 70;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 50;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID casterId = caster.getUniqueId();

        // Shift - перемикання режиму (БЕЗ кулдауну і БЕЗ витрат)
        if (caster.isSneaking()) {
            ProhibitionMode currentMode = playerModes.getOrDefault(casterId, ProhibitionMode.TELEPORT_BAN);
            ProhibitionMode[] modes = ProhibitionMode.values();
            int nextIndex = (currentMode.ordinal() + 1) % modes.length;
            ProhibitionMode newMode = modes[nextIndex];

            playerModes.put(casterId, newMode);

            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

            String message = ChatColor.GOLD + "⚖ Режим: " + ChatColor.YELLOW + newMode.getDisplayName();
            caster.sendMessage(message);

            context.spawnParticle(
                    newMode.getParticle(),
                    caster.getLocation().add(0, 1, 0),
                    30,
                    0.5, 0.5, 0.5
            );

            // НЕ витрачаємо духовність і НЕ накладаємо кулдаун
            return AbilityResult.failure("");
        }

        // Звичайне використання - накладаємо заборону
        ProhibitionMode mode = playerModes.getOrDefault(casterId, ProhibitionMode.TELEPORT_BAN);
        Beyonder casterBeyonder = context.getCasterBeyonder();
        int casterSequence = casterBeyonder == null ? 9 : casterBeyonder.getSequenceLevel();

        List<Player> nearbyPlayers = context.getNearbyPlayers(RADIUS);

        showActivationEffect(context, mode);

        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        ProhibitionZone zone = new ProhibitionZone(
                context.getCasterLocation(),
                RADIUS,
                mode,
                casterSequence,
                casterId,
                System.currentTimeMillis() + (DURATION_SECONDS * 1000L)
        );

        // Застосовуємо заборону на всіх в радіусі (ВКЛЮЧАЮЧИ КАСТЕРА)
        for (Player target : nearbyPlayers) {
            UUID targetId = target.getUniqueId();
            Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);
            int targetSequence = targetBeyonder == null ? 9 : targetBeyonder.getSequenceLevel();

            // Кастер завжди під забороною (не може опиратися власній заборі)
            if (targetId.equals(casterId)) {
                zone.addBannedPlayer(targetId);
                affectedPlayers.add(targetId);
                showProhibitionEffect(context, target, mode);
                sendProhibitionMessage(target, mode, true);
                continue;
            }

            // Інші можуть опиратися якщо вони потойбічні кращої послідовності
            boolean canResist = targetBeyonder != null && targetSequence < casterSequence;

            if (canResist) {
                double resistChance = (casterSequence - targetSequence) * 0.15;
                if (Math.random() < resistChance) {
                    resistedPlayers.add(targetId);
                    showResistEffect(context, target);
                    target.sendMessage(ChatColor.GREEN + "⚖ Ви опиралися забороні!");
                    continue;
                }
            }

            zone.addBannedPlayer(targetId);
            affectedPlayers.add(targetId);
            showProhibitionEffect(context, target, mode);
            sendProhibitionMessage(target, mode, false);
        }

        // Активуємо зону
        activeZones.add(zone);
        subscribeToProhibitionEvents(context, zone);
        maintainZoneVisuals(context, zone);

        // Автоматичне видалення після закінчення
        context.scheduleDelayed(() -> {
            activeZones.remove(zone);
            announceZoneExpiration(context, zone);
        }, DURATION_TICKS);

        showResultStatistics(context, mode, affectedPlayers, resistedPlayers);

        return AbilityResult.success();
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
        context.subscribeToEvent(
                PlayerTeleportEvent.class,
                event -> activeZones.contains(zone) && zone.isBanned(event.getPlayer().getUniqueId()) && zone.isInside(event.getPlayer().getLocation()),
                event -> {
                    event.setCancelled(true);
                    Player player = event.getPlayer();

                    sendActionBar(player, ChatColor.RED + "⚖ Телепортація заборонена!");
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);

                    notifyCasterOfViolation(context, zone, player, "спробував телепортуватися");
                    context.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeKillBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.subscribeToEvent(
                EntityDamageByEntityEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;
                    if (!(event.getDamager() instanceof Player attacker)) return false;
                    if (!(event.getEntity() instanceof Player victim)) return false;
                    if (!zone.isInside(victim.getLocation())) return false;
                    if (!zone.isBanned(attacker.getUniqueId())) return false;

                    // Перевіряємо чи удар вб'є гравця
                    double damageAfter = victim.getHealth() - event.getFinalDamage();
                    return damageAfter <= 0;
                },
                event -> {
                    Player victim = (Player) event.getEntity();
                    Player attacker = (Player) event.getDamager();

                    // Встановлюємо здоров'я на мінум замість смерті
                    context.scheduleDelayed(() -> {
                        if (victim.getHealth() <= 0) {
                            victim.setHealth(1.0);
                        }
                    }, 1L);

                    sendActionBar(attacker, ChatColor.RED + "⚖ Вбивство заборонено!");
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);

                    notifyCasterOfViolation(context, zone, attacker, "спробував вбити " + victim.getName());

                    context.spawnParticle(Particle.TOTEM_OF_UNDYING, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
                    context.playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeEntryBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.subscribeToEvent(
                PlayerMoveEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;

                    Player player = event.getPlayer();
                    if (!zone.isBanned(player.getUniqueId())) return false;

                    Location to = event.getTo();
                    if (to == null) return false;

                    // Перевіряємо чи гравець намагається увійти в чужий домен
                    UUID owner = AreaOfJurisdiction.getDomainOwnerAt(to);
                    return owner != null && !owner.equals(player.getUniqueId());
                },
                event -> {
                    // Обробник входу в домен: сильне відштовхування + 5 секунд на вихід, інакше покарання
                    Player player = event.getPlayer();
                    Location to = event.getTo();
                    if (to == null) return;

                    UUID owner = AreaOfJurisdiction.getDomainOwnerAt(to);
                    if (owner == null) return;

                    // Сильний відштовх назад від центру домену
                    AreaOfJurisdiction.DomainData data = AreaOfJurisdiction.getDomainData(owner);
                    if (data == null) return;

                    Vector away = player.getLocation().toVector().subtract(data.getCenter().toVector()).normalize();
                    // якщо нульовий вектор (точно в центрі) — використаємо напрямок з гравця
                    if (away.isZero()) away = player.getLocation().getDirection().clone().multiply(-1);
                    away.setY(0.9);
                    away = away.multiply(1.8);
                    player.setVelocity(away);

                    // actionbar - повідомлення про обмежений час
                    sendActionBar(player, ChatColor.RED + "⚖ Ви в чужому домені! Вийдіть за 5с або буде покарання.");
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);

                    UUID playerId = player.getUniqueId();

                    // Якщо вже відстежується — просто оновимо таймер
                    intrusionDomainOwner.put(playerId, owner);
                    intrusionRemainingSeconds.put(playerId, 5);

                    // Стартуємо повторювану перевірку (без можливості явного відписання від таску — але таск перевіряє карти)
                    context.scheduleRepeating(() -> {
                        // Якщо гравець більше не відстежується — нічого не робимо
                        if (!intrusionDomainOwner.containsKey(playerId)) return;
                        UUID trackedOwner = intrusionDomainOwner.get(playerId);
                        if (!trackedOwner.equals(owner)) {
                            intrusionDomainOwner.remove(playerId);
                            intrusionRemainingSeconds.remove(playerId);
                            return;
                        }

                        // Якщо гравець вийшов з домену — прибираємо відстеження і повідомляємо
                        if (!AreaOfJurisdiction.isLocationInsideDomainForOwner(player.getLocation(), owner)) {
                            intrusionDomainOwner.remove(playerId);
                            intrusionRemainingSeconds.remove(playerId);
                            sendActionBar(player, ChatColor.GREEN + "⚖ Ви вийшли з домену.");
                            return;
                        }

                        // Зменшуємо таймер
                        Integer rem = intrusionRemainingSeconds.get(playerId);
                        if (rem == null) {
                            intrusionDomainOwner.remove(playerId);
                            return;
                        }

                        if (rem <= 0) {
                            // Викликаємо покарання — використовуючи owner як "кастер" домену
                            intrusionDomainOwner.remove(playerId);
                            intrusionRemainingSeconds.remove(playerId);

                            // реєструємо порушення: "Втручання у домен"
                            Punishment.registerViolation(context, owner, playerId, "втручання у домен");

                            // Додаткові ефекти
                            context.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4);
                            context.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 0.6f);

                            return;
                        } else {
                            intrusionRemainingSeconds.put(playerId, rem - 1);
                            sendActionBar(player, ChatColor.YELLOW + "⚖ Вийдіть з домену: " + ChatColor.WHITE + rem + "с");
                        }

                    }, 0L, 20L);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeFlightBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.scheduleRepeating(() -> {
            if (!activeZones.contains(zone)) return;
            for (UUID playerId : zone.bannedPlayers) {
                Player player = context.getCaster().getServer().getPlayer(playerId);
                if (player == null || !player.isOnline()) continue;
                if (!zone.isInside(player.getLocation())) continue;

                if (player.isFlying() || player.getAllowFlight()) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    sendActionBar(player, ChatColor.RED + "⚖ Політ заборонено!");
                    player.playSound(player.getLocation(), Sound.ENTITY_BAT_DEATH, 1.0f, 0.8f);

                    notifyCasterOfViolation(context, zone, player, "спробував летіти");
                    context.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                }
            }
        }, 0L, 5L);
    }

    private void subscribeWeaponUseBlocking(IAbilityContext context, ProhibitionZone zone) {
        // Блокуємо використання зброї ТІЛЬКИ при атаці когось
        context.subscribeToEvent(
                EntityDamageByEntityEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;
                    if (!(event.getDamager() instanceof Player attacker)) return false;
                    if (!zone.isInside(attacker.getLocation())) return false;

                    // Переконаємось, що заборона працює також на кастера навіть якщо щось не так з bannedPlayers
                    if (!zone.isBanned(attacker.getUniqueId()) && !attacker.getUniqueId().equals(zone.casterId)) return false;

                    org.bukkit.inventory.ItemStack item = attacker.getInventory().getItemInMainHand();
                    return isBannedWeapon(item.getType());
                },
                event -> {
                    event.setCancelled(true);
                    Player attacker = (Player) event.getDamager();

                    sendActionBar(attacker, ChatColor.RED + "⚖ Використання зброї заборонено!");
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);

                    notifyCasterOfViolation(context, zone, attacker, "спробував атакувати забороненою зброєю");

                    context.spawnParticle(Particle.SMOKE, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeEnvironmentLock(IAbilityContext context, ProhibitionZone zone) {
        // existing code unchanged
        context.subscribeToEvent(
                ExplosionPrimeEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getEntity().getLocation()),
                ev -> {
                    ev.setCancelled(true);
                    context.playSound(ev.getEntity().getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                },
                DURATION_TICKS + 20
        );

        context.subscribeToEvent(
                CreatureSpawnEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getLocation()),
                ev -> ev.setCancelled(true),
                DURATION_TICKS + 20
        );

        context.subscribeToEvent(
                BlockBreakEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getBlock().getLocation()),
                ev -> {
                    ev.setCancelled(true);
                    Player p = ev.getPlayer();
                    if (zone.isBanned(p.getUniqueId())) {
                        sendActionBar(p, ChatColor.RED + "⚖ Зміни блоків заборонені!");
                        notifyCasterOfViolation(context, zone, p, "спробував зруйнувати блок");
                    }
                },
                DURATION_TICKS + 20
        );

        context.subscribeToEvent(
                BlockPlaceEvent.class,
                ev -> activeZones.contains(zone) && zone.isInside(ev.getBlock().getLocation()),
                ev -> {
                    ev.setCancelled(true);
                    Player p = ev.getPlayer();
                    if (zone.isBanned(p.getUniqueId())) {
                        sendActionBar(p, ChatColor.RED + "⚖ Зміни блоків заборонені!");
                        notifyCasterOfViolation(context, zone, p, "спробував поставити блок");
                    }
                },
                DURATION_TICKS + 20
        );

        context.subscribeToEvent(
                WeatherChangeEvent.class,
                ev -> {
                    if (!activeZones.contains(zone)) return false;
                    World w = (World) ev.getWorld();
                    Location center = zone.center;
                    return w.equals(center.getWorld());
                },
                ev -> ev.setCancelled(true),
                DURATION_TICKS + 20
        );
    }

    private boolean isBannedWeapon(org.bukkit.Material material) {
        String name = material.name();
        return name.contains("SWORD") ||
                name.contains("AXE") ||
                name.contains("BOW") ||
                name.contains("CROSSBOW") ||
                name.contains("TRIDENT");
    }

    private void notifyCasterOfViolation(IAbilityContext context, ProhibitionZone zone,
                                         Player violator, String action) {
        Player caster = context.getCaster().getServer().getPlayer(zone.casterId);

        // Не повідомляємо якщо сам кастер порушив
        if (violator.getUniqueId().equals(zone.casterId)) return;

        if (caster == null || !caster.isOnline()) return;

        // Повідомлення кастеру в action bar
        String casterMsg = ChatColor.GOLD + "⚖ " + ChatColor.YELLOW + violator.getName() +
                ChatColor.GRAY + " " + action;
        sendActionBar(caster, casterMsg);
        caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f);

        // Тригеримо Punishment якщо кастер має sequence <= 5
        Beyonder casterBeyonder = context.getBeyonderFromEntity(zone.casterId);
        if (casterBeyonder != null && casterBeyonder.getSequenceLevel() <= 5) {
            Punishment.registerViolation(context, zone.casterId, violator.getUniqueId(), action);
        }
    }

    private void announceZoneExpiration(IAbilityContext context, ProhibitionZone zone) {
        for (UUID playerId : zone.bannedPlayers) {
            Player player = context.getCaster().getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.GRAY + "⚖ Заборона знята.");
                context.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                        10, 0.3, 0.5, 0.3);
            }
        }
    }

    private void showActivationEffect(IAbilityContext context, ProhibitionMode mode) {
        Location loc = context.getCasterLocation();

        context.playSphereEffect(loc.clone().add(0, 1, 0), RADIUS, mode.getParticle(), 40);
        context.playWaveEffect(loc, RADIUS, Particle.CRIT, 30);

        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);

        context.sendMessageToCaster(ChatColor.GOLD + "⚖ Активовано заборону: " +
                ChatColor.YELLOW + mode.getDisplayName());
    }

    private void showProhibitionEffect(IAbilityContext context, Player target, ProhibitionMode mode) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.spawnParticle(mode.getParticle(), loc, 30, 0.5, 0.5, 0.5);
        context.spawnParticle(Particle.CRIT, loc, 20, 0.3, 0.5, 0.3);
        context.playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f);
    }

    private void showResistEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);
        context.spawnParticle(Particle.ENCHANT, loc, 50, 0.5, 0.5, 0.5);
        context.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
    }

    private void sendProhibitionMessage(Player target, ProhibitionMode mode, boolean isCaster) {
        String prefix = isCaster ?
                ChatColor.GOLD + "⚖ Ви накладаєте на себе заборону: " :
                ChatColor.RED + "⚖ На вас накладено заборону: ";

        target.sendMessage(prefix + ChatColor.YELLOW + mode.getDisplayName());
        target.sendMessage(ChatColor.GRAY + mode.getDescription());
    }

    private void showResultStatistics(IAbilityContext context, ProhibitionMode mode,
                                      Set<UUID> affected, Set<UUID> resisted) {
        int total = affected.size() + resisted.size();

        if (total == 0) {
            context.sendMessageToCaster(ChatColor.YELLOW + "У зоні дії немає цілей");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.GOLD).append("═══ Результат Заборони ═══\n");

        if (!affected.isEmpty()) {
            message.append(ChatColor.RED)
                    .append("✓ Під забороною: ")
                    .append(ChatColor.WHITE)
                    .append(affected.size())
                    .append("\n");
        }

        if (!resisted.isEmpty()) {
            message.append(ChatColor.GREEN)
                    .append("✗ Опиралися: ")
                    .append(ChatColor.WHITE)
                    .append(resisted.size())
                    .append("\n");
        }

        int successRate = total == 0 ? 0 : (int) ((affected.size() * 100.0) / total);
        message.append(ChatColor.GRAY)
                .append("Успішність: ")
                .append(ChatColor.YELLOW)
                .append(successRate)
                .append("%");

        context.sendMessageToCaster(message.toString());
    }

    private void maintainZoneVisuals(IAbilityContext context, ProhibitionZone zone) {
        context.scheduleRepeating(() -> {
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
            context.spawnParticle(zone.mode.getParticle(), particleLoc, 1, 0.0, 0.0, 0.0);
        }
    }

    private static void sendActionBar(Player p, String message) {
        if (p == null || !p.isOnline()) return;
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (NoClassDefFoundError | NoSuchMethodError ex) {
            p.sendMessage(message);
        }
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
        activeZones.clear();
        intrusionDomainOwner.clear();
        intrusionRemainingSeconds.clear();
    }

    // enums and ProhibitionZone unchanged
    public enum ProhibitionMode {
        TELEPORT_BAN(
                "Заборона телепортації",
                "Телепортація в зоні неможлива",
                Particle.PORTAL
        ),
        KILL_BAN(
                "Заборона вбивств",
                "Вбивство гравців неможливе (дамаг дозволено)",
                Particle.TOTEM_OF_UNDYING
        ),
        JURISDICTION_ENTRY_BAN(
                "Заборона входу в домен",
                "Вхід у чужий домен заборонено",
                Particle.SOUL
        ),
        FLIGHT_BAN(
                "Заборона польоту",
                "Політ в зоні неможливий",
                Particle.CLOUD
        ),
        WEAPON_USE_BAN(
                "Заборона зброї",
                "Використання зброї повністю заблоковано",
                Particle.SMOKE
        ),
        ENVIRONMENT_LOCK(
                "Блокування середовища",
                "Підриви, спавн, зміни блоків та погода заблоковані",
                Particle.WITCH
        );

        private final String displayName;
        private final String description;
        private final Particle particle;

        ProhibitionMode(String displayName, String description, Particle particle) {
            this.displayName = displayName;
            this.description = description;
            this.particle = particle;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public Particle getParticle() {
            return particle;
        }
    }

    public static class ProhibitionZone {
        final Location center;
        final double radius;
        final ProhibitionMode mode;
        final int casterSequence;
        final UUID casterId;
        final long expirationTime;
        final Set<UUID> bannedPlayers;

        public ProhibitionZone(Location center, double radius, ProhibitionMode mode,
                               int casterSequence, UUID casterId, long expirationTime) {
            this.center = center;
            this.radius = radius;
            this.mode = mode;
            this.casterSequence = casterSequence;
            this.casterId = casterId;
            this.expirationTime = expirationTime;
            this.bannedPlayers = ConcurrentHashMap.newKeySet();
        }

        public void addBannedPlayer(UUID playerId) {
            bannedPlayers.add(playerId);
        }

        public boolean isBanned(UUID playerId) {
            return bannedPlayers.contains(playerId);
        }

        public boolean isInside(Location loc) {
            if (!loc.getWorld().equals(center.getWorld())) return false;
            return loc.distance(center) <= radius;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProhibitionZone that)) return false;
            return Objects.equals(center, that.center) &&
                    expirationTime == that.expirationTime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(center, expirationTime);
        }
    }
}