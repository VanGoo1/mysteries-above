package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class EscapeTrick extends ActiveAbility {

    private static final double PULL_STRENGTH = 1.3;
    private static final double SMOKE_RADIUS = 2.5;
    private static final int SMOKE_DURATION = 60; // 3 секунди
    private static final int COST = 35;
    private static final int COOLDOWN = 10;
    @Override
    public String getName() {
        return "Трюк з діабло";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Різко відтягує кастера назад, залишаючи густу димову завісу. " +
                "Усі, хто заходить у дим, сповільнюються.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        World world = caster.getWorld();

        Location start = caster.getLocation();
        Location smokeCenter = start.clone().add(0, 1, 0);

        // Основний густий дим
        world.spawnParticle(
                Particle.LARGE_SMOKE,
                smokeCenter,
                120,
                1.2, 1.0, 1.2,
                0.02
        );

        world.spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                smokeCenter,
                80,
                0.8, 1.5, 0.8,
                0.01
        );

        world.spawnParticle(
                Particle.SQUID_INK,
                smokeCenter,
                45,
                0.7, 0.6, 0.7,
                0.0
        );

        // 👇 DUST_PLUME — важкий «киплячий» дим у центрі
        world.spawnParticle(
                Particle.DUST_PLUME,
                smokeCenter,
                60,
                1.5, 0.8, 1.5,
                0.0
        );

        world.playSound(
                smokeCenter,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.4f,
                0.6f
        );

        Vector pullBack = caster.getLocation()
                .getDirection()
                .normalize()
                .multiply(-PULL_STRENGTH);

        pullBack.setY(0.25);
        caster.setVelocity(pullBack);

        world.playSound(
                caster.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT,
                0.6f,
                1.8f
        );

        context.scheduleRepeating(new Runnable() {
            int ticksLeft = SMOKE_DURATION;

            @Override
            public void run() {
                if (ticksLeft <= 0) return;

                // Дим продовжує "жити"
                world.spawnParticle(
                        Particle.DUST_PLUME,
                        smokeCenter,
                        25,
                        1.3, 0.6, 1.3,
                        0.0
                );

                for (Player target : world.getPlayers()) {
                    if (target.equals(caster)) continue;

                    if (target.getLocation().distanceSquared(smokeCenter)
                            <= SMOKE_RADIUS * SMOKE_RADIUS) {

                        context.applyEffect(
                                target.getUniqueId(),
                                PotionEffectType.SLOWNESS,
                                20,
                                1
                        );
                    }
                }

                ticksLeft -= 5;
            }
        }, 0, 5);

        return AbilityResult.success();
    }
}
