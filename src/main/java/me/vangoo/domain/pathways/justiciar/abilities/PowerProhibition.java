package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.pathways.justiciar.abilities.AreaOfJurisdiction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.potion.PotionEffect;
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

    // Структури для відслідковування вторгнень у домени
    private static final Map<UUID, Integer> intrusionRemainingSeconds = new ConcurrentHashMap<>();
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

        // 1. Shift - Перемикання режиму
        if (caster.isSneaking()) {
            ProhibitionMode currentMode = playerModes.getOrDefault(casterId, ProhibitionMode.TELEPORT_BAN);
            ProhibitionMode[] modes = ProhibitionMode.values();
            int nextIndex = (currentMode.ordinal() + 1) % modes.length;
            ProhibitionMode newMode = modes[nextIndex];

            playerModes.put(casterId, newMode);
            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

            context.sendMessageToActionBar(
                    Component.text("⚖ Режим: ", NamedTextColor.GOLD)
                            .append(LegacyComponentSerializer.legacySection().deserialize(newMode.getDisplayName()))
            );
            context.spawnParticle(newMode.getParticle(), caster.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
            return AbilityResult.deferred();
        }

        // 2. Активація заборони
        ProhibitionMode mode = playerModes.getOrDefault(casterId, ProhibitionMode.TELEPORT_BAN);
        Beyonder casterBeyonder = context.getCasterBeyonder();

        // Визначаємо рівень і радіус динамічно
        int casterSequence = casterBeyonder == null ? 9 : casterBeyonder.getSequenceLevel();
        double dynamicRadius = getJurisdictionRadius(casterSequence);

        List<Player> nearbyPlayers = context.getNearbyPlayers(dynamicRadius);

        // Ефекти активації з новим радіусом
        Location loc = context.getCasterLocation();
        context.playSphereEffect(loc.clone().add(0, 1, 0), dynamicRadius, mode.getParticle(), 40);
        context.playWaveEffect(loc, dynamicRadius, Particle.CRIT, 30);
        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);

        context.sendMessageToActionBar(
                Component.text("⚖ Активовано заборону: ", NamedTextColor.GOLD)
                        .append(LegacyComponentSerializer.legacySection().deserialize(mode.getDisplayName()))
        );

        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        ProhibitionZone zone = new ProhibitionZone(
                context.getCasterLocation(),
                dynamicRadius, // Передаємо динамічний радіус
                mode,
                casterSequence,
                casterId,
                System.currentTimeMillis() + (DURATION_SECONDS * 1000L)
        );

        // Початковий скан гравців (для ефектів і статистики)
        for (Player target : nearbyPlayers) {
            UUID targetId = target.getUniqueId();
            Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);
            int targetSequence = targetBeyonder == null ? 9 : targetBeyonder.getSequenceLevel();

            if (targetId.equals(casterId)) {
                zone.addBannedPlayer(targetId);
                affectedPlayers.add(targetId);
                showProhibitionEffect(context, target, mode);
                sendProhibitionMessage(context, target, mode, true);
                continue;
            }

            // Перевірка резисту при касті (для статистики)
            // Увага: Реальна перевірка для входу тепер буде в subscribeEntryBlocking
            boolean isStronger = targetBeyonder != null && targetSequence < casterSequence;
            if (isStronger) {
                // Шанс резисту для самої заборони, якщо вона накладається прямо на гравця
                if (Math.random() < 0.5) {
                    resistedPlayers.add(targetId);
                    showResistEffect(context, target);
                    continue;
                }
            }

            zone.addBannedPlayer(targetId);
            affectedPlayers.add(targetId);
            showProhibitionEffect(context, target, mode);
            sendProhibitionMessage(context, target, mode, false);
        }

        activeZones.add(zone);
        subscribeToProhibitionEvents(context, zone);
        maintainZoneVisuals(context, zone);

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

                    // ACTION BAR: Порушник
                    context.sendMessageToActionBar(player, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.RED + "⚖ Телепортація заборонена!"
                    ));
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
                    // 1. Базові перевірки
                    if (!activeZones.contains(zone)) return false;
                    if (!(event.getDamager() instanceof Player attacker)) return false;
                    if (!(event.getEntity() instanceof Player victim)) return false;

                    // 2. Перевірка локації жертви (чи в зоні вона)
                    if (!zone.isInside(victim.getLocation())) return false;

                    // 3. ПЕРЕВІРКА АТАКУЮЧОГО:
                    // Блокуємо, якщо атакуючий є в списку забанених АБО якщо атакуючий - це КАСТЕР.
                    // Це гарантує, що кастер не зможе обійти заборону.
                    boolean isAttackerBanned = zone.isBanned(attacker.getUniqueId());
                    boolean isAttackerCaster = attacker.getUniqueId().equals(zone.casterId);

                    if (!isAttackerBanned && !isAttackerCaster) {
                        return false; // Якщо атакуючий не під забороною і не кастер -> дозволяємо
                    }

                    // 4. Перевірка на смертельний урон
                    double damageAfter = victim.getHealth() - event.getFinalDamage();
                    return damageAfter <= 0;
                },
                event -> {
                    // Скасовуємо смерть
                    event.setCancelled(true);

                    Player victim = (Player) event.getEntity();
                    Player attacker = (Player) event.getDamager();

                    // Емуляція Тотема Безсмертя (лікування)
                    if (victim.getHealth() < 1.0) {
                        victim.setHealth(1.0);
                    }

                    // Візуальний та звуковий ефект
                    victim.playEffect(EntityEffect.TOTEM_RESURRECT);

                    // Баффи після тотема
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));

                    // Сповіщення Атакуючому (це побачить і Кастер, якщо він атакував)
                    context.sendMessageToActionBar(attacker, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.RED + "⚖ Вбивство заборонено! (Спрацював захист)"
                    ));

                    // Сповіщення Жертві
                    context.sendMessageToActionBar(victim, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.GREEN + "⚖ Заборона врятувала вас від смерті!"
                    ));

                    attacker.playSound(attacker.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

                    // Якщо кастер намагався когось вбити — він отримає повідомлення про порушення сам на себе
                    // (або ми не надсилаємо, якщо attacker == caster, щоб не спамити,
                    // але в Actionbar він вже побачив попередження вище).
                    notifyCasterOfViolation(context, zone, attacker, "спробував вбити " + victim.getName());
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeEntryBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.subscribeToEvent(
                PlayerMoveEvent.class,
                event -> {
                    // 1. Перевірка активності зони
                    if (!activeZones.contains(zone)) return false;

                    Player player = event.getPlayer();

                    // 2. Імунітет Кастера
                    if (player.getUniqueId().equals(zone.casterId)) return false;

                    // 3. Геометрична перевірка (чи входить гравець у зону)
                    Location to = event.getTo();
                    if (to == null) return false;
                    if (!zone.isInside(to)) return false; // Якщо рух не всередину/всередині зони, ігноруємо

                    // 4. ПЕРЕВІРКА РІВНЯ СИЛ (SEQUENCE HIERARCHY)
                    Beyonder targetBeyonder = context.getBeyonderFromEntity(player.getUniqueId());

                    if (targetBeyonder != null) {
                        int targetSeq = targetBeyonder.getSequenceLevel();
                        // У Lord of the Mysteries менше число = сильніший.
                        // Якщо ціль (напр. 5) < Кастер (напр. 7), то ціль сильніша.
                        // Сильніші ігнорують бар'єр.
                        if (targetSeq < zone.casterSequence) {
                            return false; // Дозволити вхід
                        }
                    }

                    // Якщо рівень слабший або рівний, або це не бійондер — блокуємо
                    return true;
                },
                event -> {
                    Player player = event.getPlayer();

                    // Вектор відштовхування
                    Location center = zone.center.clone();
                    Vector direction = player.getLocation().toVector().subtract(center.toVector()).normalize();

                    if (Double.isNaN(direction.getX())) direction = new Vector(0, 0, 1);

                    // Відштовхуємо назад
                    player.setVelocity(direction.multiply(1.2).setY(0.4));

                    context.sendMessageToActionBar(player, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.RED + "⛔ Рівень заборони занадто високий для вас!"
                    ));

                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
                    context.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
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

                    // ACTION BAR: Порушник
                    context.sendMessageToActionBar(player, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.RED + "⚖ Політ заборонено!"
                    ));
                    player.playSound(player.getLocation(), Sound.ENTITY_BAT_DEATH, 1.0f, 0.8f);

                    notifyCasterOfViolation(context, zone, player, "спробував летіти");
                    context.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                }
            }
        }, 0L, 5L);
    }

    private void subscribeWeaponUseBlocking(IAbilityContext context, ProhibitionZone zone) {
        context.subscribeToEvent(
                EntityDamageByEntityEvent.class,
                event -> {
                    if (!activeZones.contains(zone)) return false;
                    if (!(event.getDamager() instanceof Player attacker)) return false;
                    if (!zone.isInside(attacker.getLocation())) return false;
                    if (!zone.isBanned(attacker.getUniqueId()) && !attacker.getUniqueId().equals(zone.casterId)) return false;
                    org.bukkit.inventory.ItemStack item = attacker.getInventory().getItemInMainHand();
                    return isBannedWeapon(item.getType());
                },
                event -> {
                    event.setCancelled(true);
                    Player attacker = (Player) event.getDamager();

                    // ACTION BAR: Порушник
                    context.sendMessageToActionBar(attacker, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.RED + "⚖ Використання зброї заборонено!"
                    ));
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);

                    notifyCasterOfViolation(context, zone, attacker, "спробував атакувати забороненою зброєю");
                    context.spawnParticle(Particle.SMOKE, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
                },
                DURATION_TICKS + 20
        );
    }

    private void subscribeEnvironmentLock(IAbilityContext context, ProhibitionZone zone) {
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
                        context.sendMessageToActionBar(p, LegacyComponentSerializer.legacySection().deserialize(
                                ChatColor.RED + "⚖ Зміни блоків заборонені!"
                        ));
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
                        context.sendMessageToActionBar(p, LegacyComponentSerializer.legacySection().deserialize(
                                ChatColor.RED + "⚖ Зміни блоків заборонені!"
                        ));
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
        return name.contains("SWORD") || name.contains("AXE") || name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT");
    }

    private void notifyCasterOfViolation(IAbilityContext context, ProhibitionZone zone, Player violator, String action) {
        Player caster = context.getCaster().getServer().getPlayer(zone.casterId);
        if (violator.getUniqueId().equals(zone.casterId)) return;
        if (caster == null || !caster.isOnline()) return;

        // ACTION BAR: Кастер (Сповіщення про порушення)
        context.sendMessageToActionBar(caster, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GOLD + "⚖ " + ChatColor.YELLOW + violator.getName() + ChatColor.GRAY + " " + action
        ));
        caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f);

        Beyonder casterBeyonder = context.getBeyonderFromEntity(zone.casterId);
        if (casterBeyonder != null && casterBeyonder.getSequenceLevel() <= 5) {
            Punishment.registerViolation(context, zone.casterId, violator.getUniqueId(), action);
        }
    }

    private void announceZoneExpiration(IAbilityContext context, ProhibitionZone zone) {
        for (UUID playerId : zone.bannedPlayers) {
            Player player = context.getCaster().getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                // ACTION BAR: Закінчення дії
                context.sendMessageToActionBar(player, LegacyComponentSerializer.legacySection().deserialize(
                        ChatColor.GRAY + "⚖ Заборона знята."
                ));
                context.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3);
            }
        }
    }

    private void showActivationEffect(IAbilityContext context, ProhibitionMode mode) {
        Location loc = context.getCasterLocation();
        context.playSphereEffect(loc.clone().add(0, 1, 0), RADIUS, mode.getParticle(), 40);
        context.playWaveEffect(loc, RADIUS, Particle.CRIT, 30);
        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);

        // ACTION BAR: Кастер (Активація)
        context.sendMessageToActionBar(
                Component.text("⚖ Активовано заборону: ", NamedTextColor.GOLD)
                        .append(LegacyComponentSerializer.legacySection().deserialize(mode.getDisplayName()))
        );
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

    private void sendProhibitionMessage(IAbilityContext context, Player target, ProhibitionMode mode, boolean isCaster) {
        String prefix = isCaster ?
                ChatColor.GOLD + "⚖ Ви наклали заборону: " :
                ChatColor.RED + "⚖ На вас накладено заборону: ";

        // ACTION BAR: Ціль (або Кастер про себе)
        // Об'єднуємо назву і опис, щоб помістилося в одну стрічку actionbar
        context.sendMessageToActionBar(target, LegacyComponentSerializer.legacySection().deserialize(
                prefix + ChatColor.YELLOW + mode.getDisplayName()
        ));
    }

    private void showResultStatistics(IAbilityContext context, ProhibitionMode mode, Set<UUID> affected, Set<UUID> resisted) {
        int total = affected.size() + resisted.size();
        if (total == 0) {
            context.sendMessageToCaster(ChatColor.YELLOW + "У зоні дії немає цілей");
            return;
        }

        // Статистика залишається в чаті, оскільки вона занадто велика для Action Bar
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.GOLD).append("═══ Результат Заборони ═══\n");
        if (!affected.isEmpty()) {
            message.append(ChatColor.RED).append("✓ Під забороною: ").append(ChatColor.WHITE).append(affected.size()).append("\n");
        }
        if (!resisted.isEmpty()) {
            message.append(ChatColor.GREEN).append("✗ Опиралися: ").append(ChatColor.WHITE).append(resisted.size()).append("\n");
        }
        int successRate = total == 0 ? 0 : (int) ((affected.size() * 100.0) / total);
        message.append(ChatColor.GRAY).append("Успішність: ").append(ChatColor.YELLOW).append(successRate).append("%");

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
    // Метод для визначення радіусу згідно з логікою AreaOfJurisdiction
    private double getJurisdictionRadius(int sequence) {
        return switch (sequence) {
            case 6 -> 60.0;
            case 5 -> 100.0; // Рівень напівбога і вище
            default -> sequence < 5 ? 150.0 : 15.0;
        };
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
        activeZones.clear();
        intrusionDomainOwner.clear();
        intrusionRemainingSeconds.clear();
    }

    public enum ProhibitionMode {
        TELEPORT_BAN("Заборона телепортації", "Телепортація в зоні неможлива", Particle.ENCHANT),
        KILL_BAN("Заборона вбивств", "Вбивство гравців неможливе", Particle.ENCHANT),
        JURISDICTION_ENTRY_BAN("Заборона входу в Cферу Юрисдикції", "Вхід у чужий домен заборонено", Particle.ENCHANT),
        FLIGHT_BAN("Заборона польоту", "Політ в зоні неможливий", Particle.ENCHANT),
        WEAPON_USE_BAN("Заборона зброї", "Використання зброї заблоковано", Particle.ENCHANT),
        ENVIRONMENT_LOCK("Блокування середовища", "Зміни блоків заблоковані", Particle.ENCHANT);

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