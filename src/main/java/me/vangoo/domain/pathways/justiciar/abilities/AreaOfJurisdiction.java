package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
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
 * Активна здібність. Створює зону радіусом 50 блоків.
 * Всередині зони кастер отримує Resistance I та Haste I.
 * Позиція зберігається до перезавантаження серверу.
 */
public class AreaOfJurisdiction extends ActiveAbility {

    private static final int COST = 150;
    private static final int COOLDOWN = 60; // 1 хвилина
    private static final int DOMAIN_RADIUS = 50;
    private static final int DOMAIN_RADIUS_SQUARED = DOMAIN_RADIUS * DOMAIN_RADIUS;

    // Зберігає центр домену для кожного гравця (Static = живе до рестарту)
    private static final Map<UUID, Location> activeDomains = new ConcurrentHashMap<>();

    // Змінні для контролю глобального таймера
    private static boolean isTaskRunning = false;
    private static BukkitTask domainTask = null;

    @Override
    public String getName() {
        return "Сфера Юрисдикції";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Встановлює територію закону (радіус " + DOMAIN_RADIUS + " блоків) у вашій поточній позиції.\n" +
                "У цій зоні ви отримуєте " + ChatColor.GRAY + "Опір I" + ChatColor.RESET +
                " та " + ChatColor.YELLOW + "Поспіх I" + ChatColor.RESET + ".\n";
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
        Location center = caster.getLocation();

        // 1. Оновлюємо або додаємо домен гравця
        activeDomains.put(caster.getUniqueId(), center);

        // 2. Запускаємо глобальний моніторинг, якщо він ще не працює
        startGlobalMonitoringIfNotRunning();

        // 3. Візуалізація створення домену
        playCreationEffects(center);

        context.sendMessageToCaster(ChatColor.GOLD + "⚖ Ви встановили свою Територію. Закон на вашому боці.");
        context.playSoundToCaster(Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);

        return AbilityResult.success();
    }

    /**
     * Запускає глобальний таймер, який перевіряє позиції всіх Арбітрів.
     * Запускається лише один раз на весь сервер.
     */
    private void startGlobalMonitoringIfNotRunning() {
        if (isTaskRunning) return;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("Mysteries-Above"); // Назва вашого плагіна
        if (plugin == null) return;

        isTaskRunning = true;

        // Запускаємо перевірку кожну секунду (20 тіків)
        domainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDomains.isEmpty()) return;

            for (Map.Entry<UUID, Location> entry : activeDomains.entrySet()) {
                UUID playerId = entry.getKey();
                Location domainCenter = entry.getValue();

                Player p = Bukkit.getPlayer(playerId);

                // Якщо гравець офлайн або в іншому світі - пропускаємо
                if (p == null || !p.isOnline() || !p.getWorld().equals(domainCenter.getWorld())) {
                    continue;
                }

                // Перевірка дистанції (використовуємо distanceSquared для оптимізації)
                if (p.getLocation().distanceSquared(domainCenter) <= DOMAIN_RADIUS_SQUARED) {
                    applyDomainBuffs(p);
                }
            }
        }, 0L, 20L);
    }

    private void applyDomainBuffs(Player p) {
        // Накладаємо ефекти на 2.5 секунди (50 тіків).
        // Оскільки таймер оновлюється кожну 1 сек, ефекти будуть безкінечними, поки гравець в зоні.
        // Як тільки вийде - ефекти зникнуть через 1-2 сек.
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, 0, false, false, true));

        // Легкий візуал, що бафи активні (золота іскра біля ніг)
        if (Math.random() < 0.3) {
            p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private void playCreationEffects(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Звук удару молотка
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.5f);

        // Малюємо коло на землі (радіус 5, щоб позначити центр, а не весь регіон 50 блоків)
        double radius = 5.0;
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location point = center.clone().add(x, 0.1, z);
            world.spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0);
        }

        // Стовп світла в центрі
        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1, 0), 20, 0.2, 2, 0.2, 0.05);
    }

    public static void cleanup() {
        if (domainTask != null && !domainTask.isCancelled()) {
            domainTask.cancel();
        }
        activeDomains.clear();
        isTaskRunning = false;
    }
}