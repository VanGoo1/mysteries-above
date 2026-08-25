package me.vangoo.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Agility extends PermanentPassiveAbility {

    private static final int REFRESH_PERIOD_TICKS = 100; // 5 секунд
    private static final int EFFECT_DURATION_TICKS = 120; // 6 секунд
    private static final double SAFE_FALL_BLOCKS = 20.0;  // з 21-го блока — пів сердечка, далі по наростаючій

    /** Ключ підписки на падіння — власний, щоб чужий unsubscribeAll(casterId) її не зніс. */
    private final Map<UUID, UUID> fallSubscriptions = new ConcurrentHashMap<>();

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
        applySpeed(context);
        registerFallProtection(context);
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID subKey = fallSubscriptions.remove(context.getCasterId());
        if (subKey != null) context.events().unsubscribeAll(subKey);

        Player player = context.getCasterPlayer();
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null) return;

        // Самолікування: після /reload з онлайн-гравцями onActivate не викликається взагалі,
        // тож підписка на падіння відновлюється звідси. Ключ у мапі робить це ідемпотентним.
        if (!fallSubscriptions.containsKey(context.getCasterId())) {
            registerFallProtection(context);
        }

        // Підтримка Швидкості (зі скейлінгом). Відлік по гравцю, а не по часу світу:
        // getFullTime() застигає при doDaylightCycle=false.
        if (player.getTicksLived() % REFRESH_PERIOD_TICKS == 0) {
            applySpeed(context);
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

    private void registerFallProtection(IAbilityContext context) {
        UUID playerId = context.getCasterId();
        UUID previous = fallSubscriptions.get(playerId);
        if (previous != null) context.events().unsubscribeAll(previous);

        UUID subKey = UUID.randomUUID();
        fallSubscriptions.put(playerId, subKey);
        Bukkit.getLogger().info("[MA][Agility] fall protection subscribed for " + playerId);

        context.events().subscribeToTemporaryEvent(subKey,
                EntityDamageEvent.class,
                event -> event.getEntity().getUniqueId().equals(playerId)
                        && event.getCause() == EntityDamageEvent.DamageCause.FALL,
                event -> {
                    double fallDistance = ((Player) event.getEntity()).getFallDistance();
                    // Деякі реалізації скидають fallDistance до події — відновлюємо з ванільної шкоди.
                    if (fallDistance <= 0) fallDistance = event.getDamage() + 3;
                    // Рахуємо від висоти падіння, а не від поточної шкоди: обробник ідемпотентний.
                    double newDamage = Math.max(0, Math.ceil(fallDistance - SAFE_FALL_BLOCKS));
                    newDamage = Math.min(newDamage, event.getDamage()); // ніколи не більше ванільної

                    Bukkit.getLogger().info("[MA][Agility] fall=" + fallDistance
                            + " vanilla=" + event.getDamage() + " -> " + newDamage);

                    Location landLocation = event.getEntity().getLocation().add(0, 0.2, 0);

                    // Завжди скасовуємо подію: на Arclight setDamage() для FALL не діє,
                    // і залишок урону лишався б ванільним. Решту завдаємо самі, наступним
                    // тіком — damage() всередині обробника події реентрантний.
                    event.setCancelled(true);

                    if (newDamage <= 0) {
                        context.effects().playWaveEffect(landLocation, 2.5, Particle.WHITE_ASH, 10);
                        context.effects().playSound(landLocation, Sound.BLOCK_WOOL_STEP, 1.4f, 1.0f);
                    } else {
                        double residual = newDamage;
                        context.scheduling().scheduleDelayed(
                                () -> context.entity().damage(playerId, residual), 1L);
                        context.effects().playSound(landLocation, Sound.ENTITY_GENERIC_SMALL_FALL, 1.0f, 0.8f);
                    }
                },
                Integer.MAX_VALUE
        );
    }
}
