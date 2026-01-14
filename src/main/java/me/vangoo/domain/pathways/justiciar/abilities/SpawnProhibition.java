package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain Ability: Spawn Prohibition (Заборона Спавну)
 *
 * Активна здібність з різними режимами:
 * 1. Заборона зомбі - видаляє зомбі в радіусі та блокує їх спавн
 * 2. Заборона скелетів - видаляє скелетів в радіусі та блокує їх спавн
 * 3. Заборона пауків - видаляє пауків в радіусі та блокує їх спавн
 * 4. Заборона криперів - видаляє криперів в радіусі та блокує їх спавн
 * 5. Заборона ворожих мобів - видаляє всіх ворожих мобів та блокує їх спавн
 *
 * Shift+ПКМ - переключення режиму
 * ПКМ - використання вибраного режиму
 */
public class SpawnProhibition extends ActiveAbility {

    private static final double RADIUS = 30.0;
    private static final int DURATION_SECONDS = 60; // 1 хвилина
    private static final int DURATION_TICKS = DURATION_SECONDS * 20;

    // Зберігаємо поточний режим для кожного гравця
    private static final Map<UUID, ProhibitionMode> playerModes = new ConcurrentHashMap<>();

    // Зберігаємо активні зони заборони спавну
    private static final Set<SpawnBanZone> activeSpawnBans = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "Заборона Спавну";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Накладає заборону на спавн мобів.\n" +
                ChatColor.GRAY + "▪ Радіус: " + ChatColor.WHITE + (int)RADIUS + " блоків\n" +
                ChatColor.GRAY + "▪ Тривалість: " + ChatColor.WHITE + DURATION_SECONDS + " секунд\n" +
                ChatColor.GRAY + "▪ Shift+ПКМ: " + ChatColor.WHITE + "переключити режим\n" +
                ChatColor.GRAY + "▪ ПКМ: " + ChatColor.WHITE + "використати заборону\n" +
                ChatColor.YELLOW + "Режими:\n" +
                ChatColor.GRAY + "  • Заборона зомбі\n" +
                ChatColor.GRAY + "  • Заборона скелетів\n" +
                ChatColor.GRAY + "  • Заборона пауків\n" +
                ChatColor.GRAY + "  • Заборона криперів\n" +
                ChatColor.GRAY + "  • Заборона ворожих мобів";
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 60;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID casterId = caster.getUniqueId();

        // Якщо Shift натиснуто - це перемикання режиму (безкоштовно)
        if (caster.isSneaking()) {
            ProhibitionMode currentMode = playerModes.getOrDefault(casterId, ProhibitionMode.ZOMBIE);
            ProhibitionMode newMode = currentMode.next();

            playerModes.put(casterId, newMode);

            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

            // ACTION BAR: Зміна режиму
            context.sendMessageToActionBar(
                    Component.text("⚖ Режим: ", NamedTextColor.GOLD)
                            .append(LegacyComponentSerializer.legacySection().deserialize(newMode.getDisplayName()))
            );

            // Візуальний ефект
            Particle particle = getParticleForMode(newMode);
            context.spawnParticle(
                    particle,
                    caster.getLocation().add(0, 1, 0),
                    20,
                    0.5, 0.5, 0.5
            );

            return AbilityResult.deferred();
        }

        // Отримуємо поточний режим
        ProhibitionMode mode = playerModes.getOrDefault(casterId, ProhibitionMode.ZOMBIE);

        Location centerLoc = context.getCasterLocation();

        // Видаляємо мобів в радіусі
        int removedCount = removeMobsInRadius(context, mode, centerLoc);

        // Створюємо зону заборони спавну
        SpawnBanZone zone = new SpawnBanZone(
                centerLoc,
                RADIUS,
                System.currentTimeMillis() + (DURATION_SECONDS * 1000L),
                mode
        );

        activeSpawnBans.add(zone);

        // Підписуємося на події спавну
        subscribeSpawnBlocking(context, zone);

        // Візуальні ефекти
        showActivationEffect(context, mode, removedCount);

        // Автоматичне видалення зони після закінчення часу
        context.scheduleDelayed(() -> {
            activeSpawnBans.remove(zone);

            // ACTION BAR: Повідомлення про завершення
            if (caster != null && caster.isOnline()) {
                context.sendMessageToActionBar(caster, LegacyComponentSerializer.legacySection().deserialize(
                        ChatColor.GRAY + "⚖ Заборона спавну " + mode.getDisplayName() + " завершилася"
                ));
            }
        }, DURATION_TICKS);

        // Підтримка візуалізації зони
        maintainZoneVisuals(context, zone);

        return AbilityResult.success();
    }

    /**
     * Видаляє мобів відповідного типу в радіусі
     */
    private int removeMobsInRadius(IAbilityContext context, ProhibitionMode mode, Location center) {
        World world = center.getWorld();
        if (world == null) return 0;

        int removed = 0;

        // Отримуємо всіх entities в радіусі
        for (Entity entity : world.getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
            if (shouldRemoveEntity(entity, mode)) {
                // Візуальний ефект перед видаленням
                Location loc = entity.getLocation();
                world.spawnParticle(Particle.POOF, loc, 20, 0.3, 0.5, 0.3, 0.05);
                world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);

                // Видаляємо
                entity.remove();
                removed++;
            }
        }

        return removed;
    }

    /**
     * Перевіряє чи потрібно видалити цю entity
     */
    private boolean shouldRemoveEntity(Entity entity, ProhibitionMode mode) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof Player) return false;

        return switch (mode) {
            case ZOMBIE -> entity instanceof Zombie;
            case SKELETON -> entity instanceof Skeleton;
            case SPIDER -> entity instanceof Spider || entity instanceof CaveSpider;
            case CREEPER -> entity instanceof Creeper;
            case HOSTILE -> entity instanceof Monster;
        };
    }

    /**
     * Підписка на блокування спавну
     */
    private void subscribeSpawnBlocking(IAbilityContext context, SpawnBanZone zone) {
        context.subscribeToEvent(
                CreatureSpawnEvent.class,
                event -> {
                    // Перевіряємо чи зона ще активна
                    if (!activeSpawnBans.contains(zone)) {
                        return false;
                    }

                    // Пропускаємо спавн від спавнерів, яєць, команд
                    CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
                    if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER ||
                            reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
                            reason == CreatureSpawnEvent.SpawnReason.COMMAND ||
                            reason == CreatureSpawnEvent.SpawnReason.CUSTOM) {
                        return false;
                    }

                    Location spawnLoc = event.getLocation();
                    Entity entity = event.getEntity();

                    // Перевіряємо чи спавн в зоні заборони
                    if (!zone.isInside(spawnLoc)) {
                        return false;
                    }

                    // Перевіряємо чи це заборонений тип
                    return shouldRemoveEntity(entity, zone.mode);
                },
                event -> {
                    event.setCancelled(true);

                    // Візуальний ефект блокування
                    Location loc = event.getLocation();
                    World world = loc.getWorld();
                    if (world != null) {
                        world.spawnParticle(
                                Particle.SMOKE,
                                loc.add(0, 0.5, 0),
                                1,
                                0, 0, 0,
                                0
                        );
                        // Тихий звук, щоб не спамити, якщо спавнів багато
                        // world.playSound(loc, Sound.BLOCK_IRON_DOOR_CLOSE, 0.1f, 1.8f);
                    }
                },
                DURATION_TICKS + 20
        );
    }

    /**
     * Підтримка візуалізації зони заборони
     */
    private void maintainZoneVisuals(IAbilityContext context, SpawnBanZone zone) {
        context.scheduleRepeating(() -> {
            if (!activeSpawnBans.contains(zone)) {
                return;
            }

            // Малюємо межі зони
            drawZoneBoundary(context, zone);

        }, 0L, 40L); // Кожні 2 секунди
    }

    /**
     * Малює візуальні межі зони заборони
     */
    private void drawZoneBoundary(IAbilityContext context, SpawnBanZone zone) {
        Location center = zone.center;
        double radius = zone.radius;
        Particle particle = getParticleForMode(zone.mode);

        // Малюємо коло на землі
        for (int angle = 0; angle < 360; angle += 20) {
            double radians = Math.toRadians(angle);
            double x = center.getX() + radius * Math.cos(radians);
            double z = center.getZ() + radius * Math.sin(radians);

            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.2, z);

            context.spawnParticle(
                    particle,
                    particleLoc,
                    1,
                    0.0, 0.0, 0.0
            );
        }
    }

    /**
     * Показує ефект активації здібності
     */
    private void showActivationEffect(IAbilityContext context, ProhibitionMode mode, int removedCount) {
        Location loc = context.getCasterLocation();
        Particle particle = getParticleForMode(mode);

        // Сфера що розширюється
        context.playSphereEffect(
                loc.clone().add(0, 1, 0),
                RADIUS,
                particle,
                40
        );

        // Хвиля на землі
        context.playWaveEffect(
                loc,
                RADIUS,
                Particle.SWEEP_ATTACK,
                30
        );

        // Звуки
        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);

        // ACTION BAR: Активація + статистика
        StringBuilder msg = new StringBuilder();
        msg.append(ChatColor.GOLD).append("⚖ Активовано: ").append(ChatColor.YELLOW).append(mode.getDisplayName());

        if (removedCount > 0) {
            msg.append(ChatColor.GRAY).append(" (Видалено: ").append(ChatColor.WHITE).append(removedCount).append(")");
        }

        context.sendMessageToActionBar(
                LegacyComponentSerializer.legacySection().deserialize(msg.toString())
        );
    }

    /**
     * Отримує частинку для режиму
     */
    private Particle getParticleForMode(ProhibitionMode mode) {
        return switch (mode) {
            case ZOMBIE -> Particle.DAMAGE_INDICATOR;
            case SKELETON -> Particle.CRIT;
            case SPIDER -> Particle.WITCH;
            case CREEPER -> Particle.EXPLOSION;
            case HOSTILE -> Particle.ENCHANTED_HIT;
        };
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
        activeSpawnBans.clear();
    }

    /**
     * Перевіряє чи можна заспавнити моба (публічний метод для використання іншими системами)
     */
    public static boolean canSpawn(Entity entity, Location location) {
        for (SpawnBanZone zone : activeSpawnBans) {
            if (zone.isInside(location)) {
                if (entity instanceof Zombie && zone.mode == ProhibitionMode.ZOMBIE) return false;
                if (entity instanceof Skeleton && zone.mode == ProhibitionMode.SKELETON) return false;
                if ((entity instanceof Spider || entity instanceof CaveSpider) &&
                        zone.mode == ProhibitionMode.SPIDER) return false;
                if (entity instanceof Creeper && zone.mode == ProhibitionMode.CREEPER) return false;
                if (entity instanceof Monster && zone.mode == ProhibitionMode.HOSTILE) return false;
            }
        }
        return true;
    }

    /**
     * Режими заборони
     */
    private enum ProhibitionMode {
        ZOMBIE("Заборона зомбі"),
        SKELETON("Заборона скелетів"),
        SPIDER("Заборона пауків"),
        CREEPER("Заборона криперів"),
        HOSTILE("Заборона ворожих мобів");

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
     * Зона заборони спавну
     */
    private static class SpawnBanZone {
        private final Location center;
        private final double radius;
        private final long expirationTime;
        private final ProhibitionMode mode;

        public SpawnBanZone(Location center, double radius, long expirationTime, ProhibitionMode mode) {
            this.center = center;
            this.radius = radius;
            this.expirationTime = expirationTime;
            this.mode = mode;
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
            if (!(o instanceof SpawnBanZone that)) return false;
            return Objects.equals(center, that.center) &&
                    expirationTime == that.expirationTime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(center, expirationTime);
        }
    }
}