package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PsychologicalInvisibility extends ActiveAbility {

    private static final int COST_PER_SECOND = 20;
    private static final int COOLDOWN_SECONDS = 5;

    private static final Map<UUID, Runnable> activeCancellers = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Психологічна невидимість";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Абсолютна невидимість. Ви бачите себе, інші — ні. Моби ігнорують вас.";
    }

    @Override
    public int getSpiritualityCost() {
        return 0;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN_SECONDS;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID uuid = caster.getUniqueId();
        Beyonder beyonder = context.getCasterBeyonder();

        if (activeCancellers.containsKey(uuid)) {
            // Ручна деактивація - БЕЗ кулдауну
            disableInvisibility(context, caster, "деактивація", false);
            return AbilityResult.success();
        }

        if (beyonder.getSpirituality().current() < COST_PER_SECOND) {
            return AbilityResult.failure("Недостатньо духовності.");
        }

        enableInvisibility(context, caster);
        return AbilityResult.success();
    }

    private void enableInvisibility(IAbilityContext context, Player caster) {
        UUID uuid = caster.getUniqueId();

        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        context.sendMessageToCaster(ChatColor.AQUA + "Ви розчинилися у свідомості світу...");

        // Додаємо ефект невидимості БЕЗ частинок - це для мобів
        caster.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,  // ambient - не показувати частинки навколо
                false   // particles - не показувати частинки взагалі
        ));

        // Ховаємо гравця від ВСІХ інших гравців (повністю, включно з бронею та предметами)
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(uuid)) {
                context.hidePlayerFromTarget(other, caster);
            }
        }

        // Гравець природно бачить себе, бо ми не ховаємо його від себе самого

        final int startDamageDealt = caster.getStatistic(Statistic.DAMAGE_DEALT);
        final double[] lastHealth = {caster.getHealth()};
        final int[] tickCounter = {0};

        BukkitRunnable br = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter[0]++;

                if (!caster.isOnline()) {
                    disableInvisibility(context, caster, "вихід", true);
                    return;
                }

                // А. Перевірка атаки
                int currentDamageDealt = caster.getStatistic(Statistic.DAMAGE_DEALT);
                if (currentDamageDealt > startDamageDealt) {
                    disableInvisibility(context, caster, "атаку", true);
                    return;
                }

                // Б. Перевірка отримання шкоди
                double currentHealth = caster.getHealth();
                if (currentHealth < lastHealth[0]) {
                    disableInvisibility(context, caster, "отримання шкоди", true);
                    return;
                }
                if (currentHealth > lastHealth[0]) {
                    lastHealth[0] = currentHealth;
                }

                // В. ЗНЯТТЯ ДУХОВНОСТІ (Раз на секунду)
                if (tickCounter[0] % 20 == 0) {
                    Beyonder currentBeyonder = context.getCasterBeyonder();
                    Spirituality oldSp = currentBeyonder.getSpirituality();

                    if (oldSp.current() < COST_PER_SECOND) {
                        disableInvisibility(context, caster, "брак духовності", true);
                        return;
                    }
                    currentBeyonder.setSpirituality(new Spirituality(oldSp.current() - COST_PER_SECOND, oldSp.maximum()));
                }

                // Г. ПІДТРИМКА ВІЗУАЛУ: ховаємо від нових гравців, які зайшли
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(uuid)) {
                        context.hidePlayerFromTarget(other, caster);
                    }
                }

                // Д. ПСИХОЛОГІЧНИЙ ВПЛИВ НА МОБІВ
                // Ефект невидимості вже робить свою роботу, але на всяк випадок
                // обнуляємо ціль у мобів, які вже почали атаку
                for (Entity entity : caster.getNearbyEntities(15, 15, 15)) {
                    if (entity instanceof Mob mob) {
                        if (mob.getTarget() != null && mob.getTarget().equals(caster)) {
                            mob.setTarget(null);
                        }
                    }
                }
            }
        };

        final BukkitTask[] taskHolder = new BukkitTask[1];

        Runnable canceller = () -> {
            if (taskHolder[0] != null) {
                taskHolder[0].cancel();
            } else {
                br.cancel();
            }
        };

        activeCancellers.put(uuid, canceller);
        taskHolder[0] = context.scheduleRepeating(br, 1L, 1L);
    }

    private void disableInvisibility(IAbilityContext context, Player caster, String reason, boolean applyCooldown) {
        UUID uuid = caster.getUniqueId();

        if (activeCancellers.containsKey(uuid)) {
            try {
                activeCancellers.get(uuid).run();
            } catch (Exception ignored) { }
            activeCancellers.remove(uuid);
        }

        if (caster.isOnline()) {
            // Знімаємо ефект невидимості
            caster.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);

            // Показуємо гравця ВСІМ (включно з ним самим)
            for (Player other : Bukkit.getOnlinePlayers()) {
                context.showPlayerToTarget(other, caster);
            }

            context.sendMessageToCaster(ChatColor.RED + "Невидимість спала: " + reason);
            context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);

            // Кулдаун тільки якщо здібність закінчилась сама
            if (applyCooldown) {
                context.setCooldown(this, getCooldown(context.getCasterBeyonder().getSequence()));
            }
        }
    }
}