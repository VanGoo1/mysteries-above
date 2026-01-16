package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class Agility extends PermanentPassiveAbility {

    private static final int REFRESH_PERIOD_TICKS = 100; // 5 секунд
    private static final int EFFECT_DURATION_TICKS = 120; // 6 секунд
    private static final double FALL_DAMAGE_REDUCTION = 17.0; // ~20 блоків

    @Override
    public String getName() {
        return "Спритність";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int speedLvl = getSpeedAmplifier(userSequence) + 1; // +1 для відображення (0 -> I)
        String roman = switch (speedLvl) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(speedLvl);
        };

        return "Ваше тіло стає легким та швидким.\n" +
                "Дає постійну Швидкість " + roman + " та дозволяє падати з висоти до 20 блоків без шкоди.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        // Отримуємо послідовність при активації
        applySpeed(context);
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID playerId = context.getCasterId();
        Location loc = context.getCasterLocation();
        if (loc.getWorld() == null) return;

        if (!context.playerData().isOnline(playerId)) return;

        // 1. Підтримка Швидкості (зі скейлінгом)
        if (loc.getWorld().getFullTime() % REFRESH_PERIOD_TICKS == 0) {
            applySpeed(context);
        }

        // 2. Обробка падіння
        if (loc.getWorld().getFullTime() % 20 == 0) {
            registerFallProtection(context, playerId);
        }
    }

    private void applySpeed(IAbilityContext context) {
        int amplifier = getSpeedAmplifier(context.getCasterBeyonder().getSequence());
        // addPotionEffect автоматично оновлює ефект, якщо новий сильніший або такий самий
        context.entity().applyPotionEffect(context.getCasterId(), PotionEffectType.SPEED, EFFECT_DURATION_TICKS, amplifier);
    }

    /**
     * Розраховує рівень ефекту (amplifier) на основі послідовності.
     * Amplifier 0 = Speed I
     * Amplifier 1 = Speed II
     * Amplifier 2 = Speed III
     */
    private int getSpeedAmplifier(Sequence sequence) {
        int level = sequence.level();

        if (level <= 1) { // 1 та 0 послідовність
            return 2; // Швидкість III
        } else if (level <= 4) { // 4, 3, 2 послідовність
            return 1; // Швидкість II
        } else { // 7 - 5 послідовність
            return 0; // Швидкість I
        }
    }

    private void registerFallProtection(IAbilityContext context, UUID playerId) {
        context.events().subscribeToTemporaryEvent(playerId,
                EntityDamageEvent.class,
                event -> event.getEntity().getUniqueId().equals(playerId) && event.getCause() == EntityDamageEvent.DamageCause.FALL,
                event -> {
                    double originalDamage = event.getDamage();
                    double newDamage = Math.max(0, originalDamage - FALL_DAMAGE_REDUCTION);

                    if (newDamage == 0 && originalDamage > 0) {
                        event.setCancelled(true);

                        // Локація трохи вище землі для кращого візуалу
                        Location landLocation = context.getCasterLocation().add(0, 0.2, 0);

                        context.effects().playWaveEffect(
                                landLocation,
                                2.5,
                                Particle.CLOUD,
                                10
                        );

                        context.effects().playSphereEffect(
                                landLocation,
                                1.2,
                                Particle.WHITE_ASH,
                                15
                        );

                        context.effects().playSound(landLocation, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.6f);
                        context.effects().playSound(landLocation, Sound.BLOCK_WOOL_STEP, 1.4f, 1.0f);

                    } else {
                        event.setDamage(newDamage);
                        context.effects().playSound(context.getCasterLocation(), Sound.ENTITY_GENERIC_SMALL_FALL, 1.0f, 0.8f);
                    }
                },
                25
        );
    }
}