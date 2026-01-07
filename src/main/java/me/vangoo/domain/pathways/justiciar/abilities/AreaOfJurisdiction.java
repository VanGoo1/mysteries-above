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
 *
 * Активна здібність. Створює зону що масштабується з послідовністю.
 * Всередині зони кастер отримує Resistance I та Haste I.
 * Позиція зберігається до перезавантаження серверу.
 */
public class AreaOfJurisdiction extends ActiveAbility {

    private static final int COST = 150;
    private static final int COOLDOWN = 60; // 1 хвилина
    private static final int BASE_DOMAIN_RADIUS = 50; // Базовий радіус для Sequence 9

    // Зберігає центр домену та радіус для кожного гравця
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
                " та " + ChatColor.YELLOW + "Поспіх I" + ChatColor.RESET + ".\n" +
                ChatColor.DARK_GRAY + "Радіус масштабується з послідовністю";
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
        Player caster = context.getCaster();
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

    /**
     * Запускає глобальний таймер, який перевіряє позиції всіх Арбітрів.
     * Запускається лише один раз на весь сервер.
     */
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
        World world = center.getWorld();
        if (world == null) return;

        // Звук удару молотка
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);

        // Малюємо коло на землі (показуємо 10% від реального радіусу для візуалізації центру)
        double visualRadius = Math.min(scaledRadius * 0.1, 10.0);
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * visualRadius;
            double z = Math.sin(angle) * visualRadius;

            Location point = center.clone().add(x, 0.1, z);
            world.spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0);
        }

        // Додаткові кола для показу повного радіусу (на 25%, 50%, 75%, 100%)
        for (double percent : new double[]{0.25, 0.5, 0.75, 1.0}) {
            double r = scaledRadius * percent;
            // Показуємо менше частинок для віддалених кіл
            int particleCount = (int)(360 / (10 * percent));

            for (int i = 0; i < 360; i += particleCount) {
                double angle = Math.toRadians(i);
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;

                Location point = center.clone().add(x, 0.1, z);
                world.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
        }

        // Стовп світла в центрі
        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1, 0), 20, 0.2, 2, 0.2, 0.05);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 0.5, 0), 3, 0, 0, 0, 0);
    }

    public static void cleanup() {
        if (domainTask != null && !domainTask.isCancelled()) {
            domainTask.cancel();
        }
        activeDomains.clear();
        isTaskRunning = false;
    }

    /**
     * Внутрішній клас для зберігання даних домену
     */
    private static class DomainData {
        final Location center;
        final int radius;

        DomainData(Location center, int radius) {
            this.center = center;
            this.radius = radius;
        }
    }
}