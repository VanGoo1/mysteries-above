package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
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

    private static final int COST_PER_SECOND = 16;
    private static final int COOLDOWN_SECONDS = 5;

    private static final Map<UUID, Runnable> activeCancellers = new ConcurrentHashMap<>();
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
        return 0;
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
        Player caster = context.getCasterPlayer();
        UUID id = caster.getUniqueId();
        Beyonder beyonder = context.getCasterBeyonder();

        // Вимкнення вручну
        if (activeCancellers.containsKey(id)) {
            disable(context, caster, "свідоме розкриття", true);
            return AbilityResult.success();
        }

        if (beyonder.getSpirituality().current() < COST_PER_SECOND) {
            context.sendMessageToActionBar(
                    caster,
                    Component.text("✗ Недостатньо духовності")
            );
            return AbilityResult.failure("Недостатньо духовності");
        }

        enable(context, caster);
        return AbilityResult.deferred();
    }

    private void enable(IAbilityContext context, Player caster) {
        UUID id = caster.getUniqueId();

        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        context.sendMessageToActionBar(
                caster,
                Component.text("✦ Ви зникли зі сприйняття світу")
        );

        caster.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false
        ));

        // Ховаємо від усіх гравців
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(id)) {
                context.hidePlayerFromTarget(other, caster);
            }
        }

        final int startDamage = caster.getStatistic(Statistic.DAMAGE_DEALT);
        final double[] lastHealth = {caster.getHealth()};
        final int[] ticks = {0};
        final boolean[] projectileHit = {false};

        // Відстеження снарядів
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                ProjectileLaunchEvent.class,
                e -> e.getEntity().getShooter() instanceof Player p && p.getUniqueId().equals(id),
                e -> trackedProjectiles.add(e.getEntity().getUniqueId()),
                Integer.MAX_VALUE
        );

        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                EntityDamageByEntityEvent.class,
                e -> e.getDamager() instanceof Projectile pr && trackedProjectiles.contains(pr.getUniqueId()),
                e -> {
                    projectileHit[0] = true;
                    trackedProjectiles.remove(e.getDamager().getUniqueId());
                },
                Integer.MAX_VALUE
        );

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                ticks[0]++;

                if (!caster.isOnline()) {
                    disable(context, caster, "вихід", true);
                    return;
                }

                // Атака
                if (caster.getStatistic(Statistic.DAMAGE_DEALT) > startDamage || projectileHit[0]) {
                    disable(context, caster, "агресія", true);
                    return;
                }

                // Отримання шкоди
                if (caster.getHealth() < lastHealth[0]) {
                    disable(context, caster, "отримання шкоди", true);
                    return;
                }
                lastHealth[0] = caster.getHealth();

                // Зняття духовності
                if (ticks[0] % 20 == 0) {
                    Beyonder b = context.getCasterBeyonder();
                    Spirituality sp = b.getSpirituality();

                    if (sp.current() < COST_PER_SECOND) {
                        disable(context, caster, "виснаження", true);
                        return;
                    }
                    b.setSpirituality(new Spirituality(
                            sp.current() - COST_PER_SECOND,
                            sp.maximum()
                    ));
                }

                // Ховаємо від нових гравців
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(id)) {
                        context.hidePlayerFromTarget(other, caster);
                    }
                }

                // Моби ігнорують
                for (Entity e : caster.getNearbyEntities(15, 15, 15)) {
                    if (e instanceof Mob mob && caster.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                }
            }
        };

        BukkitTask task = context.scheduleRepeating(runnable, 1L, 1L);
        activeCancellers.put(id, task::cancel);
    }

    private void disable(IAbilityContext context, Player caster, String reason, boolean cooldown) {
        UUID id = caster.getUniqueId();

        if (activeCancellers.containsKey(id)) {
            activeCancellers.remove(id).run();
        }

        caster.removePotionEffect(PotionEffectType.INVISIBILITY);

        for (Player other : Bukkit.getOnlinePlayers()) {
            context.showPlayerToTarget(other, caster);
        }

        context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
        context.sendMessageToActionBar(
                caster,
                Component.text("✗ Невидимість зникла • " + reason)
        );

        if (cooldown) {
            context.setCooldown(this, getCooldown(context.getCasterBeyonder().getSequence()));
        }
    }
}
