package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.MasteryProgressionCalculator;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PsychologicalInvisibility extends ActiveAbility {

    private static final int COST_PER_SECOND = 10;
    private static final int COOLDOWN_SECONDS = 5;
    private static final long TOGGLE_SAFETY_DELAY_MS = 500;

    private static final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> activationTimes = new ConcurrentHashMap<>();
    private static final Set<UUID> trackedProjectiles = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "Психологічна невидимість";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Ви зникаєте зі сприйняття. Інші вас не бачать. " +
                "Будь-яка агресія або шкода миттєво знімає ефект.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST_PER_SECOND;
    }

    @Override
    public int getPeriodicCost() {
        return COST_PER_SECOND;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN_SECONDS;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID id = context.getCasterId();
        Beyonder beyonder = context.getCasterBeyonder();

        if (activeTasks.containsKey(id)) {
            long activatedAt = activationTimes.getOrDefault(id, 0L);
            long timeDiff = System.currentTimeMillis() - activatedAt;

            if (timeDiff < TOGGLE_SAFETY_DELAY_MS) {
                return AbilityResult.success();
            }

            disable(context, id, "свідоме розкриття", true);
            return AbilityResult.success();
        }

        if (beyonder.getSpirituality().current() < COST_PER_SECOND) {
            context.messaging().sendMessageToActionBar(
                    id,
                    Component.text("✗ Недостатньо духовності")
            );
            return AbilityResult.failure("Недостатньо духовності");
        }

        enable(context, id);
        return AbilityResult.success();
    }

    private void enable(IAbilityContext context, UUID casterId) {
        activationTimes.put(casterId, System.currentTimeMillis());

        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("✦ Ви зникли зі сприйняття світу")
        );

        // Ховаємо від усіх
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(casterId)) {
                context.entity().hidePlayerFromTarget(other.getUniqueId(), casterId);
            }
        }

        final double[] lastHealth = {context.playerData().getHealth(casterId)};
        final int[] ticks = {0};

        // Прапорець для миттєвого реагування
        final boolean[] aggressiveAction = {false};

        // --- ВИПРАВЛЕНА ЛОГІКА АГРЕСІЇ ---
        // Відстежуємо будь-яку нанесену шкоду (ближній бій АБО дальній)
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                EntityDamageByEntityEvent.class,
                e -> {
                    // 1. Якщо гравець вдарив рукою/мечем
                    if (e.getDamager().getUniqueId().equals(casterId)) {
                        return true;
                    }
                    // 2. Якщо гравець влучив з лука/арбалета/тризуба
                    if (e.getDamager() instanceof Projectile proj
                            && proj.getShooter() instanceof Player shooter
                            && shooter.getUniqueId().equals(casterId)) {
                        return true;
                    }
                    return false;
                },
                e -> {
                    // Цей код виконається, якщо умова вище = true
                    aggressiveAction[0] = true;
                    // Можна навіть викликати disable тут миттєво, не чекаючи тіка,
                    // але безпечніше залишити це для Runnable, щоб уникнути конфліктів потоків
                },
                Integer.MAX_VALUE
        );

        // Відстежуємо отримання шкоди самим гравцем (опціонально, якщо треба)
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                EntityDamageByEntityEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId),
                e -> aggressiveAction[0] = true,
                Integer.MAX_VALUE
        );

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                ticks[0]++;

                if (!context.playerData().isOnline(casterId)) {
                    disable(context, casterId, "вихід", false);
                    return;
                }

                // Перевірка прапорця агресії
                if (aggressiveAction[0]) {
                    disable(context, casterId, "агресія", true);
                    return;
                }

                // Перевірка отримання шкоди через здоров'я (резервна)
                double currentHealth = context.playerData().getHealth(casterId);
                if (lastHealth[0] - currentHealth > 0.01) {
                    disable(context, casterId, "отримання шкоди", true);
                    return;
                }
                lastHealth[0] = currentHealth;

                // --- ЛОГІКА ОНОВЛЕННЯ (PULSE) ---
                if (ticks[0] % 20 == 0) {
                    Beyonder b = context.getCasterBeyonder();
                    Spirituality sp = b.getSpirituality();

                    if (sp.current() < COST_PER_SECOND) {
                        disable(context, casterId, "виснаження", false);
                        return;
                    }

                    b.setSpirituality(sp.decrement(COST_PER_SECOND));

                    double masteryGain = MasteryProgressionCalculator.calculateMasteryGain(COST_PER_SECOND, b.getSequence());
                    if (masteryGain > 0) {
                        b.setMastery(b.getMastery().add(masteryGain));
                    }

                    // Оновлюємо ефект (щоб не мигав)
                    context.entity().applyPotionEffect(casterId, PotionEffectType.INVISIBILITY, 60, 0);
                }

                if (ticks[0] % 10 == 0) {
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (!other.getUniqueId().equals(casterId)) {
                            context.entity().hidePlayerFromTarget(other.getUniqueId(), casterId);
                        }
                    }
                }

                // Скидання агро мобів
                if (ticks[0] % 5 == 0) {
                    for (Entity e : context.targeting().getNearbyEntities(15)) {
                        if (e instanceof Mob mob && casterId.equals(mob.getTarget() != null ? mob.getTarget().getUniqueId() : null)) {
                            mob.setTarget(null);
                        }
                    }
                }
            }
        };

        BukkitTask task = context.scheduling().scheduleRepeating(runnable, 0L, 1L);
        activeTasks.put(casterId, task);
    }

    private void disable(IAbilityContext context, UUID casterId, String reason, boolean applyCooldown) {
        activationTimes.remove(casterId);

        BukkitTask task = activeTasks.remove(casterId);
        if (task != null) {
            task.cancel();
        }

        context.events().unsubscribeAll(casterId);

        // 1. Примусово знімаємо ефект (навіть якщо він сам спаде через 2 сек)
        context.entity().removePotionEffect(casterId, PotionEffectType.INVISIBILITY);

        // 2. Показуємо гравця всім
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(casterId)) {
                context.entity().showPlayerToTarget(other.getUniqueId(), casterId);
            }
        }

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("✗ Невидимість зникла • " + reason)
        );

        if (applyCooldown) {
            context.cooldown().setCooldown(this, casterId);
        }
    }
}