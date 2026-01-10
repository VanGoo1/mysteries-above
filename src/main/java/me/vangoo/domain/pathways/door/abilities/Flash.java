package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Flash extends ActiveAbility {

    private static final int RADIUS = 10;
    private static final int DURATION = 60; // 3 секунди
    private static final int COST = 50;
    private static final int COOLDOWN = 15;
    @Override
    public String getName() {
        return "Флешка";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Вивільняє сліпучий спалах, повністю дезорієнтуючи оточуючих " +
                "та дозволяючи кастеру зникнути з поля зору.";
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
        Location center = caster.getEyeLocation();
        context.spawnParticle(Particle.FLASH, center, 5
        );
        context.spawnParticle(Particle.END_ROD, caster.getLocation(), 150, 0.0, 0.0, 0.0
        );
        context.playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 2.3f
        );

        context.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 2.0f
        );

        List<Player> players = context.getNearbyPlayers(RADIUS);

        for (Player target : players) {
            context.applyEffect(target.getUniqueId(), PotionEffectType.BLINDNESS, DURATION, 1
            );

            context.hidePlayerFromTarget(target, caster);
        }
        context.scheduleDelayed(() -> {
            for (Player target : players) {
                context.showPlayerToTarget(target, caster);
            }
        }, DURATION);

        return AbilityResult.success();
    }
}
