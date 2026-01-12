package me.vangoo.domain.pathways.whitetower.abilities; // Зміни пакет на свій

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Agility extends PermanentPassiveAbility {

    private static final int REFRESH_PERIOD_TICKS = 100; // 5 секунд
    private static final int EFFECT_DURATION_TICKS = 120; // 6 секунд (із запасом)

    // Minecraft формула шкоди: (блоків_падіння - 3)
    // Ми хочемо, щоб 20 блоків не наносили шкоди.
    // 20 - 3 = 17 одиниць шкоди треба компенсувати.
    private static final double FALL_DAMAGE_REDUCTION = 17.0;

    @Override
    public String getName() {
        return "Спритність";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ваше тіло стає легким та швидким.\n" +
                "Дає постійну Швидкість I та дозволяє падати з висоти до 20 блоків без шкоди.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        applySpeed(context.getCaster());
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        Player player = context.getCaster();
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCaster();
        if (player == null || !player.isOnline()) return;

        // 1. Підтримка Швидкості
        if (player.getTicksLived() % REFRESH_PERIOD_TICKS == 0) {
            applySpeed(player);
        }

        // 2. Обробка падіння (оновлюємо підписку раз на секунду)
        if (player.getTicksLived() % 20 == 0) {
            registerFallProtection(context, player);
        }
    }

    private void applySpeed(Player player) {
        // Amplifier 0 = Speed I
        // Amplifier 1 = Speed II
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_DURATION_TICKS, 0, false, false));
    }

    private void registerFallProtection(IAbilityContext context, Player player) {
        context.subscribeToEvent(
                EntityDamageEvent.class,
                // Фільтр: подія стосується гравця і причина - падіння
                event -> event.getEntity().equals(player) && event.getCause() == EntityDamageEvent.DamageCause.FALL,
                // Логіка:
                event -> {
                    double originalDamage = event.getDamage();

                    // Зменшуємо шкоду
                    double newDamage = Math.max(0, originalDamage - FALL_DAMAGE_REDUCTION);

                    // Якщо шкода була повністю поглинута (впав з <= 20 блоків)
                    if (newDamage == 0 && originalDamage > 0) {
                        event.setCancelled(true); // Повністю скасовуємо подію (анімація почервоніння не спрацює)

                        // Візуальний ефект "м'якого приземлення"
                        context.spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.2, 0.1, 0.2);
                        context.playSound(player.getLocation(), Sound.BLOCK_WOOL_STEP, 1.0f, 1.5f);
                    } else {
                        // Якщо висота була більше 20 блоків, гравець отримає залишок шкоди
                        event.setDamage(newDamage);

                        // Звук перекату/приземлення, навіть якщо боляче
                        context.playSound(player.getLocation(), Sound.ENTITY_GENERIC_SMALL_FALL, 1.0f, 0.8f);
                    }
                },
                25 // Тривалість підписки (трохи більше 1 сек)
        );
    }
}