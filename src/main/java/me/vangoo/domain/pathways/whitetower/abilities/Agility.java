package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
        return "Ваше тіло стає легким та швидким.\n" +
                "Дає постійну Швидкість I та дозволяє падати з висоти до 20 блоків без шкоди.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        applySpeed(context.getCasterPlayer());
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
        Player player = context.getCasterPlayer();
        if (player == null || !player.isOnline()) return;

        // 1. Підтримка Швидкості
        if (player.getTicksLived() % REFRESH_PERIOD_TICKS == 0) {
            applySpeed(player);
        }

        // 2. Обробка падіння
        if (player.getTicksLived() % 20 == 0) {
            registerFallProtection(context, player);
        }
    }

    private void applySpeed(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_DURATION_TICKS, 0, false, false));
    }

    private void registerFallProtection(IAbilityContext context, Player player) {
        context.subscribeToEvent(
                EntityDamageEvent.class,
                event -> event.getEntity().equals(player) && event.getCause() == EntityDamageEvent.DamageCause.FALL,
                event -> {
                    double originalDamage = event.getDamage();
                    double newDamage = Math.max(0, originalDamage - FALL_DAMAGE_REDUCTION);

                    if (newDamage == 0 && originalDamage > 0) {
                        event.setCancelled(true);

                        // --- ВИПРАВЛЕННЯ ТУТ ---
                        // Беремо локацію і піднімаємо її на 0.2 блоку вгору.
                        // Це витягне частинки з текстури землі.
                        Location landLocation = player.getLocation().add(0, 0.2, 0);

                        // 1. Хвиля (Wave)
                        // CLOUD - це досить велика частинка, вона добре виглядає над землею
                        context.playWaveEffect(
                                landLocation,
                                2.5,
                                Particle.CLOUD,
                                10
                        );

                        // 2. Вихор (Vortex)
                        // Також використовуємо підняту локацію, щоб низ вихора не був у землі
                        context.playVortexEffect(
                                landLocation,
                                1.5,
                                1.0,
                                Particle.WHITE_ASH,
                                15
                        );

                        // Звуки залишаємо на original location (або на новій, різниці для звуку немає)
                        context.playSound(landLocation, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);
                        context.playSound(landLocation, Sound.BLOCK_WOOL_STEP, 1.5f, 1.0f);

                    } else {
                        event.setDamage(newDamage);
                        context.playSound(player.getLocation(), Sound.ENTITY_GENERIC_SMALL_FALL, 1.0f, 0.8f);
                    }
                },
                25
        );
    }
}