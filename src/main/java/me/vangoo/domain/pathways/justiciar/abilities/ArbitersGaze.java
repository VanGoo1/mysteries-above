
package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArbitersGaze extends PermanentPassiveAbility {

    private static final int STARE_THRESHOLD_SECONDS = 4;
    private static final int STARE_THRESHOLD_TICKS = STARE_THRESHOLD_SECONDS * 20;
    private static final int EFFECT_DURATION_TICKS = 10 * 20;
    private static final double MAX_DISTANCE = 15.0;

    // Зберігає стан погляду для кожного Арбітра
    private final Map<UUID, StareState> stareStates = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Погляд Судді";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ваш пильний погляд тисне на ворогів. Якщо безперервно дивитися на ціль " +
                STARE_THRESHOLD_SECONDS + " секунди, вона отримає Слабкість.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        // Ініціалізація стану при отриманні здібності
        stareStates.put(context.getCaster().getUniqueId(), new StareState());
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        // Очищення пам'яті при втраті здібності/виході
        stareStates.remove(context.getCaster().getUniqueId());
    }

    @Override
    public void tick(IAbilityContext context) {
        Player caster = context.getCaster();
        StareState state = stareStates.computeIfAbsent(caster.getUniqueId(), k -> new StareState());

        // Виконуємо RayTrace, щоб знайти сутність погляду
        // false - ігнорувати рідини, true - ігнорувати проходимі блоки (трава і т.д. не блокують погляд)
        RayTraceResult result = caster.getWorld().rayTrace(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                MAX_DISTANCE,
                FluidCollisionMode.NEVER,
                true,
                0.5, // Розмір хітбокса променя
                entity -> entity instanceof Player && !entity.getUniqueId().equals(caster.getUniqueId())
        );

        Player targetedPlayer = null;

        // Перевіряємо, чи ми влучили в гравця, і чи немає стіни перед ним
        if (result != null && result.getHitEntity() instanceof Player) {
            targetedPlayer = (Player) result.getHitEntity();
        }

        // Логіка оновлення таймера
        if (targetedPlayer != null) {
            processStare(context, caster, targetedPlayer, state);
        } else {
            // Якщо ні на кого не дивимось або дивимось в стіну - скидаємо
            state.reset();
        }
    }

    private void processStare(IAbilityContext context, Player caster, Player target, StareState state) {
        // Якщо це та сама ціль, що і в минулому тіку
        if (target.getUniqueId().equals(state.targetId)) {
            state.ticksAccumulated++;

            // Візуальний ефект накопичення ("тиск")
            if (state.ticksAccumulated % 5 == 0) {
                target.spawnParticle(Particle.ENCHANT, target.getEyeLocation().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0.05);
            }

            // Перевірка порогу часу
            if (state.ticksAccumulated >= STARE_THRESHOLD_TICKS) {
                applyDebuff(context, caster, target);
                state.reset(); // Скидаємо, щоб почати відлік наново
            }
        } else {
            // Зміна цілі -> скидаємо таймер і встановлюємо нову ціль
            state.targetId = target.getUniqueId();
            state.ticksAccumulated = 0;
        }
    }

    private void applyDebuff(IAbilityContext context, Player caster, Player target) {
        // Накладання ефекту
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, EFFECT_DURATION_TICKS, 0)); // 0 = Level 1

        // Візуал та звук успішного спрацювання
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        caster.playSound(caster.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);

        // Частинки "засудження"
        target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 15, 0.3, 0.5, 0.3, 0.1);
        target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.getLocation(), 5, 0.2, 1.0, 0.2, 0.05);

        // Повідомлення (опціонально, щоб Арбітр розумів, що спрацювало)
        caster.sendMessage(ChatColor.GOLD + "Ви придушили волю " + ChatColor.RED + target.getName());
        target.sendMessage(ChatColor.GRAY + "Ваші рухи стають слабшими під пильним поглядом...");
    }

    /**
     * Внутрішній клас для зберігання стану таймера
     */
    private static class StareState {
        UUID targetId = null;
        int ticksAccumulated = 0;

        void reset() {
            targetId = null;
            ticksAccumulated = 0;
        }
    }
}