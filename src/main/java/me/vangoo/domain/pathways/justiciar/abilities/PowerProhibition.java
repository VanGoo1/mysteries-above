package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain Ability: Prohibition of Power (Заборона Сил)
 *
 * Активна здібність з двома режимами:
 * 1. Заборона потойбічних сил - блокує використання здібностей
 * 2. Заборона телепортації - блокує телепортацію
 *
 * Shift+ПКМ - переключення режиму
 * ПКМ - використання вибраного режиму
 */
public class PowerProhibition extends ActiveAbility {

    private static final double RADIUS = 30.0;
    private static final int DURATION_SECONDS = 30;
    private static final int DURATION_TICKS = DURATION_SECONDS * 20;

    // Зберігаємо поточний режим для кожного гравця
    private static final Map<UUID, ProhibitionMode> playerModes = new ConcurrentHashMap<>();

    // Зберігаємо активні зони заборони телепортації
    private static final Set<TeleportBanZone> activeTeleportBans = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "Заборона Сил";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Накладає заборону на ворожих потойбічних.\n" +
                ChatColor.GRAY + "▪ Радіус: " + ChatColor.WHITE + (int)RADIUS + " блоків\n" +
                ChatColor.GRAY + "▪ Тривалість: " + ChatColor.WHITE + DURATION_SECONDS + " секунд\n" +
                ChatColor.GRAY + "▪ Shift+ПКМ: " + ChatColor.WHITE + "переключити режим\n" +
                ChatColor.GRAY + "▪ ПКМ: " + ChatColor.WHITE + "використати заборону\n" +
                ChatColor.YELLOW + "Режими:\n" +
                ChatColor.GRAY + "  • Заборона здібностей\n" +
                ChatColor.GRAY + "  • Заборона телепортації";
    }

    @Override
    public int getSpiritualityCost() {
        return 60;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 45;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID casterId = caster.getUniqueId();

        // Якщо Shift натиснуто - це перемикання режиму (безкоштовно)
        if (caster.isSneaking()) {
            ProhibitionMode currentMode = playerModes.getOrDefault(casterId, ProhibitionMode.ABILITY_BAN);
            ProhibitionMode newMode = currentMode.next();

            playerModes.put(casterId, newMode);

            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

            caster.sendMessage(ChatColor.GOLD + "Режим змінено: " +
                    ChatColor.YELLOW + newMode.getDisplayName());

            Particle particle = newMode == ProhibitionMode.ABILITY_BAN ?
                    Particle.ENCHANT : Particle.PORTAL;
            context.spawnParticle(
                    particle,
                    caster.getLocation().add(0, 1, 0),
                    20,
                    0.5, 0.5, 0.5
            );

            return AbilityResult.failure("");
        }

        // Отримуємо поточний режим
        ProhibitionMode mode = playerModes.getOrDefault(casterId, ProhibitionMode.ABILITY_BAN);

        Beyonder casterBeyonder = context.getCasterBeyonder();
        int casterSequence = casterBeyonder.getSequenceLevel();

        List<Player> nearbyPlayers = context.getNearbyPlayers(RADIUS);

        if (nearbyPlayers.isEmpty()) {
            return AbilityResult.failure("В радіусі немає інших гравців!");
        }

        // Показуємо початковий ефект
        showActivationEffect(context, mode);

        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        // Застосовуємо заборону відповідно до режиму
        switch (mode) {
            case ABILITY_BAN -> applyAbilityBan(
                    context, nearbyPlayers, casterSequence,
                    affectedPlayers, resistedPlayers
            );
            case TELEPORT_BAN -> applyTeleportBan(
                    context, nearbyPlayers, casterSequence,
                    affectedPlayers, resistedPlayers
            );
        }

        // Показуємо результати
        showResultStatistics(context, mode, affectedPlayers, resistedPlayers);

        return AbilityResult.success();
    }

    /**
     * Переключення режиму заборони
     */
    private AbilityResult toggleMode(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        ProhibitionMode currentMode = playerModes.getOrDefault(casterId, ProhibitionMode.ABILITY_BAN);
        ProhibitionMode newMode = currentMode.next();

        playerModes.put(casterId, newMode);

        // Візуальний та звуковий фідбек
        Player caster = context.getCaster();
        caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        String message = ChatColor.GOLD + "Режим: " +
                ChatColor.YELLOW + newMode.getDisplayName();

        // Action bar замість чату
        net.md_5.bungee.api.ChatMessageType messageType = net.md_5.bungee.api.ChatMessageType.ACTION_BAR;
        caster.spigot().sendMessage(messageType, new net.md_5.bungee.api.chat.TextComponent(message));

        // Показуємо частинки відповідно до режиму
        Particle particle = newMode == ProhibitionMode.ABILITY_BAN ?
                Particle.ENCHANT : Particle.PORTAL;
        context.spawnParticle(
                particle,
                caster.getLocation().add(0, 1, 0),
                20,
                0.5, 0.5, 0.5
        );

        return AbilityResult.successWithMessage(""); // Порожнє повідомлення
    }

    /**
     * Застосування заборони на використання здібностей
     */
    private void applyAbilityBan(
            IAbilityContext context,
            List<Player> targets,
            int casterSequence,
            Set<UUID> affected,
            Set<UUID> resisted
    ) {
        for (Player target : targets) {
            if (!target.isOnline()) continue;

            UUID targetId = target.getUniqueId();
            Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);

            if (targetBeyonder == null) continue;

            int targetSequence = targetBeyonder.getSequenceLevel();
            SequenceBasedSuccessChance successChance =
                    new SequenceBasedSuccessChance(casterSequence, targetSequence);

            if (!successChance.rollSuccess()) {
                resisted.add(targetId);
                showResistanceEffect(context, target, successChance);
                continue;
            }

            // Блокуємо здібності на 30 секунд
            context.lockAbilities(targetId, DURATION_SECONDS);
            affected.add(targetId);

            // Візуальні ефекти на цілі
            showAbilityBanEffect(context, target);

            // Повідомлення цілі
            context.sendMessage(
                    targetId,
                    ChatColor.RED + "⚠ Ваші потойбічні сили заблоковані на " +
                            DURATION_SECONDS + " секунд!"
            );
        }
    }

    /**
     * Застосування заборони на телепортацію
     */
    private void applyTeleportBan(
            IAbilityContext context,
            List<Player> targets,
            int casterSequence,
            Set<UUID> affected,
            Set<UUID> resisted
    ) {
        Location centerLoc = context.getCasterLocation();

        // Створюємо зону заборони телепортації
        TeleportBanZone zone = new TeleportBanZone(
                centerLoc,
                RADIUS,
                System.currentTimeMillis() + (DURATION_SECONDS * 1000L)
        );

        for (Player target : targets) {
            if (!target.isOnline()) continue;

            UUID targetId = target.getUniqueId();
            Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);

            if (targetBeyonder == null) continue;

            int targetSequence = targetBeyonder.getSequenceLevel();
            SequenceBasedSuccessChance successChance =
                    new SequenceBasedSuccessChance(casterSequence, targetSequence);

            if (!successChance.rollSuccess()) {
                resisted.add(targetId);
                showResistanceEffect(context, target, successChance);
                continue;
            }

            // Додаємо гравця до забороненої зони
            zone.addBannedPlayer(targetId);
            affected.add(targetId);

            // Візуальні ефекти
            showTeleportBanEffect(context, target);

            // Повідомлення
            context.sendMessage(
                    targetId,
                    ChatColor.DARK_PURPLE + "⚠ Телепортація заблокована на " +
                            DURATION_SECONDS + " секунд!"
            );
        }

        // Активуємо зону
        activeTeleportBans.add(zone);

        // Підписуємося на події телепортації
        subscribeTeleportBlocking(context, zone);

        // Візуальні ефекти зони
        maintainZoneVisuals(context, zone);

        // Автоматичне видалення зони після закінчення часу
        context.scheduleDelayed(() -> {
            activeTeleportBans.remove(zone);
        }, DURATION_TICKS);
    }

    /**
     * Підписка на блокування телепортації
     */
    private void subscribeTeleportBlocking(IAbilityContext context, TeleportBanZone zone) {
        context.subscribeToEvent(
                PlayerTeleportEvent.class,
                event -> {
                    // Перевіряємо чи зона ще активна
                    if (!activeTeleportBans.contains(zone)) {
                        return false;
                    }

                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();

                    // Перевіряємо чи гравець в забороні
                    if (!zone.isBanned(playerId)) {
                        return false;
                    }

                    Location from = event.getFrom();
                    Location to = event.getTo();

                    // Перевіряємо чи телепортація з зони або в зону
                    boolean fromInside = zone.isInside(from);
                    boolean toInside = to != null && zone.isInside(to);

                    return fromInside || toInside;
                },
                event -> {
                    event.setCancelled(true);
                    Player player = event.getPlayer();

                    player.sendMessage(ChatColor.RED + "✗ Телепортація заблокована!");
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);

                    // Візуальний ефект блокування
                    context.spawnParticle(
                            Particle.SMOKE,
                            player.getLocation().add(0, 1, 0),
                            5,
                            0.5, 0.5, 0.5
                    );
                },
                DURATION_TICKS + 20
        );
    }

    /**
     * Підтримка візуалізації зони заборони
     */
    private void maintainZoneVisuals(IAbilityContext context, TeleportBanZone zone) {
        context.scheduleRepeating(() -> {
            if (!activeTeleportBans.contains(zone)) {
                return;
            }

            // Малюємо межі зони
            drawZoneBoundary(context, zone);

        }, 0L, 20L); // Кожну секунду
    }

    /**
     * Малює візуальні межі зони заборони
     */
    private void drawZoneBoundary(IAbilityContext context, TeleportBanZone zone) {
        Location center = zone.center;
        double radius = zone.radius;

        // Малюємо коло на землі
        for (int angle = 0; angle < 360; angle += 15) {
            double radians = Math.toRadians(angle);
            double x = center.getX() + radius * Math.cos(radians);
            double z = center.getZ() + radius * Math.sin(radians);

            Location particleLoc = new Location(center.getWorld(), x, center.getY(), z);

            context.spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1,
                    0.0, 0.0, 0.0
            );

        }
    }

    /**
     * Показує ефект активації здібності
     */
    private void showActivationEffect(IAbilityContext context, ProhibitionMode mode) {
        Location loc = context.getCasterLocation();

        // Сфера що розширюється
        context.playSphereEffect(
                loc.clone().add(0, 1, 0),
                RADIUS,
                mode == ProhibitionMode.ABILITY_BAN ? Particle.ENCHANT : Particle.PORTAL,
                40
        );

        // Хвиля на землі
        context.playWaveEffect(
                loc,
                RADIUS,
                Particle.CRIT,
                30
        );

        // Звуки
        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);

        String modeName = mode == ProhibitionMode.ABILITY_BAN ?
                "потойбічних сил" : "телепортації";
        context.sendMessageToCaster(
                ChatColor.GOLD + "Активовано заборону " + ChatColor.YELLOW + modeName
        );
    }

    /**
     * Ефект блокування здібностей на цілі
     */
    private void showAbilityBanEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.spawnParticle(
                Particle.ENCHANT,
                loc,
                30,
                0.5, 0.5, 0.5
        );

        context.spawnParticle(
                Particle.CRIT,
                loc,
                20,
                0.3, 0.5, 0.3
        );

        context.playSound(
                loc,
                Sound.ENTITY_EVOKER_CAST_SPELL,
                1.0f,
                0.8f
        );
    }

    /**
     * Ефект блокування телепортації на цілі
     */
    private void showTeleportBanEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.spawnParticle(
                Particle.PORTAL,
                loc,
                40,
                0.5, 0.5, 0.5
        );

        context.spawnParticle(
                Particle.SMOKE,
                loc,
                10,
                0.3, 0.5, 0.3
        );

        context.playSound(
                loc,
                Sound.BLOCK_PORTAL_TRIGGER,
                1.0f,
                0.5f
        );
    }

    /**
     * Показує ефект опору
     */
    private void showResistanceEffect(
            IAbilityContext context,
            Player target,
            SequenceBasedSuccessChance successChance
    ) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.spawnParticle(
                Particle.FIREWORK,
                loc,
                20,
                0.5, 0.5, 0.5
        );

        context.playSound(
                loc,
                Sound.ITEM_SHIELD_BLOCK,
                1.0f,
                1.2f
        );

        context.sendMessage(
                target.getUniqueId(),
                ChatColor.GREEN + "Ви зміг опиратися забороні! " +
                        ChatColor.GRAY + "(Шанс: " + successChance.getFormattedChance() + ")"
        );
    }

    /**
     * Показує статистику результатів
     */
    private void showResultStatistics(
            IAbilityContext context,
            ProhibitionMode mode,
            Set<UUID> affected,
            Set<UUID> resisted
    ) {
        int total = affected.size() + resisted.size();

        if (total == 0) {
            context.sendMessageToCaster(
                    ChatColor.YELLOW + "Всі цілі покинули радіус дії"
            );
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.GOLD).append("═══ Результат Заборони ═══\n");

        String banType = mode == ProhibitionMode.ABILITY_BAN ?
                "здібності" : "телепортації";

        if (!affected.isEmpty()) {
            message.append(ChatColor.RED)
                    .append("✓ Заблоковано ").append(banType).append(": ")
                    .append(ChatColor.WHITE)
                    .append(affected.size())
                    .append("\n");
        }

        if (!resisted.isEmpty()) {
            message.append(ChatColor.GREEN)
                    .append("✗ Опирались: ")
                    .append(ChatColor.WHITE)
                    .append(resisted.size())
                    .append("\n");
        }

        int successRate = (int)((affected.size() * 100.0) / total);
        message.append(ChatColor.GRAY)
                .append("Успішність: ")
                .append(ChatColor.YELLOW)
                .append(successRate)
                .append("%");

        context.sendMessageToCaster(message.toString());

        // Візуальний фідбек
        if (successRate >= 70) {
            context.spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    context.getCasterLocation().add(0, 2, 0),
                    30,
                    0.5, 0.5, 0.5
            );
            context.playSoundToCaster(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
        activeTeleportBans.clear();
    }

    /**
     * Перевіряє чи гравець може телепортуватися (публічний метод для використання іншими здібностями)
     */
    public static boolean canTeleport(UUID playerId, Location from, Location to) {
        for (TeleportBanZone zone : activeTeleportBans) {
            if (!zone.isBanned(playerId)) continue;

            boolean fromInside = zone.isInside(from);
            boolean toInside = zone.isInside(to);

            if (fromInside || toInside) {
                return false;
            }
        }
        return true;
    }

    /**
     * Режими заборони
     */
    private enum ProhibitionMode {
        ABILITY_BAN("Заборона здібностей"),
        TELEPORT_BAN("Заборона телепортації");

        private final String displayName;

        ProhibitionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ProhibitionMode next() {
            ProhibitionMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /**
     * Зона заборони телепортації
     */
    private static class TeleportBanZone {
        private final Location center;
        private final double radius;
        private final long expirationTime;
        private final Set<UUID> bannedPlayers;

        public TeleportBanZone(Location center, double radius, long expirationTime) {
            this.center = center;
            this.radius = radius;
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
            if (!loc.getWorld().equals(center.getWorld())) {
                return false;
            }
            return loc.distance(center) <= radius;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TeleportBanZone that)) return false;
            return Objects.equals(center, that.center) &&
                    expirationTime == that.expirationTime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(center, expirationTime);
        }
    }
}