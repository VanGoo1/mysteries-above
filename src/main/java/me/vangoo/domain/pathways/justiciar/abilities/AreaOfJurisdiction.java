package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 9 Arbiter: Tribunal Domain
 */
public class AreaOfJurisdiction extends ActiveAbility {

    private static final int COST = 80;
    private static final int COOLDOWN = 70; // 1 хвилина
    private static final int BASE_DOMAIN_RADIUS = 50; // Базовий радіус для Sequence 9

    // Зберігає центр домену та радіус для кожного гравця (owner UUID -> DomainData)
    private static final Map<UUID, DomainData> activeDomains = new ConcurrentHashMap<>();

    // Змінні для контролю глобального таймера
    private static boolean isTaskRunning = false;
    private static BukkitTask domainTask = null;

    @Override
    public String getName() {
        return "Сфера Юрисдикції";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int radius = scaleValue(BASE_DOMAIN_RADIUS, userSequence, SequenceScaler.ScalingStrategy.DIVINE);

        return "Встановлює територію закону (радіус " + radius + " блоків) у вашій поточній позиції.\n" +
                "У цій зоні ви отримуєте " + ChatColor.GRAY + "Опір I" + ChatColor.RESET +
                " та " + ChatColor.YELLOW + "Квапливість I" + ChatColor.RESET + ".\n";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        Beyonder beyonder = context.getCasterBeyonder();
        Location center = caster.getLocation();

        // Розраховуємо радіус на основі послідовності
        int scaledRadius = scaleValue(BASE_DOMAIN_RADIUS, beyonder.getSequence(), SequenceScaler.ScalingStrategy.DIVINE);

        // 1. Оновлюємо або додаємо домен гравця з новим радіусом
        activeDomains.put(caster.getUniqueId(), new DomainData(center, scaledRadius));

        // 2. Запускаємо глобальний моніторинг, якщо він ще не працює
        startGlobalMonitoringIfNotRunning();

        // 3. Візуалізація створення домену
        playCreationEffects(center, scaledRadius);

        context.sendMessageToCaster(
                ChatColor.GOLD + "⚖ Ви встановили свою Територію " +
                        ChatColor.GRAY + "(" + scaledRadius + " блоків)"
        );
        context.sendMessageToCaster(ChatColor.YELLOW + "Закон на вашому боці.");
        context.playSoundToCaster(Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);

        return AbilityResult.success();
    }

    private void startGlobalMonitoringIfNotRunning() {
        if (isTaskRunning) return;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("Mysteries-Above");
        if (plugin == null) return;

        isTaskRunning = true;

        // Запускаємо перевірку кожну секунду (20 тіків)
        domainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDomains.isEmpty()) return;

            for (Map.Entry<UUID, DomainData> entry : activeDomains.entrySet()) {
                UUID playerId = entry.getKey();
                DomainData domainData = entry.getValue();

                Player p = Bukkit.getPlayer(playerId);

                // Якщо гравець офлайн або в іншому світі - пропускаємо
                if (p == null || !p.isOnline() || !p.getWorld().equals(domainData.center.getWorld())) {
                    continue;
                }

                // Перевірка дистанції (використовуємо distanceSquared для оптимізації)
                int radiusSquared = domainData.radius * domainData.radius;
                if (p.getLocation().distanceSquared(domainData.center) <= radiusSquared) {
                    applyDomainBuffs(p);
                }
            }
        }, 0L, 20L);
    }

    private void applyDomainBuffs(Player p) {
        // Накладаємо ефекти на 2.5 секунди (50 тіків).
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, 0, false, false, true));

        // Легкий візуал, що бафи активні
        if (Math.random() < 0.3) {
            p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private void playCreationEffects(Location center, int scaledRadius) {
        if (center.getWorld() == null) return;

        try {
            World world = center.getWorld();

            world.playSound(center, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.5f);

            double visualRadius = Math.min(scaledRadius * 0.1, 10.0);

            for (int i = 0; i < 360; i += 10) {
                double angle = Math.toRadians(i);
                double x = Math.cos(angle) * visualRadius;
                double z = Math.sin(angle) * visualRadius;

                Location point = center.clone().add(x, 0.1, z);

                world.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
            }

            world.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mysteries-Above] Помилка візуалізації: " + e.getMessage());
        }
    }

    // ПОВЕРТАЄ true якщо гравець знаходиться всередині чужого домену (тобто owner != player)
    public static boolean isInsideDomain(Player player) {
        DomainData dataForOwner = activeDomains.get(player.getUniqueId());
        // Якщо у гравця свій домен — перевіряємо тільки щоб не вважати себе чужим доменом
        for (Map.Entry<UUID, DomainData> e : activeDomains.entrySet()) {
            UUID owner = e.getKey();
            DomainData d = e.getValue();
            if (!player.getWorld().equals(d.center.getWorld())) continue;
            if (player.getUniqueId().equals(owner)) continue; // ігноруємо власний домен
            if (player.getLocation().distanceSquared(d.center) <= (d.radius * d.radius)) return true;
        }
        return false;
    }

    // НОВИЙ: повертає UUID власника домену який містить вказану локацію, або null
    public static UUID getDomainOwnerAt(Location loc) {
        if (loc == null) return null;
        for (Map.Entry<UUID, DomainData> e : activeDomains.entrySet()) {
            UUID owner = e.getKey();
            DomainData d = e.getValue();
            if (!loc.getWorld().equals(d.center.getWorld())) continue;
            if (loc.distanceSquared(d.center) <= (d.radius * d.radius)) return owner;
        }
        return null;
    }

    // допоміжний: перевіряє чи локація всередині конкретного домену власника
    public static boolean isLocationInsideDomainForOwner(Location loc, UUID owner) {
        DomainData d = activeDomains.get(owner);
        if (d == null) return false;
        if (!loc.getWorld().equals(d.center.getWorld())) return false;
        return loc.distanceSquared(d.center) <= (d.radius * d.radius);
    }

    // Повертає DomainData (публічно для інших класів)
    public static DomainData getDomainData(UUID owner) {
        return activeDomains.get(owner);
    }

    public static void cleanup() {
        if (domainTask != null && !domainTask.isCancelled()) {
            domainTask.cancel();
        }
        activeDomains.clear();
        isTaskRunning = false;
    }

    public static class DomainData {
        private final Location center;
        private final int radius;

        public DomainData(Location center, int radius) {
            this.center = center;
            this.radius = radius;
        }

        public Location getCenter() {
            return center;
        }

        public int getRadius() {
            return radius;
        }
    }

}