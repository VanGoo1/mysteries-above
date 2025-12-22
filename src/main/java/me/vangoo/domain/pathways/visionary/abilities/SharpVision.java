package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.stream.Collectors;

public class SharpVision extends ActiveAbility {
    private static final int RANGE = 100;
    private static final int DURATION_SECONDS = 30;
    private static final int DURATION_TICKS = DURATION_SECONDS * 20;

    @Override
    public String getName() {
        return "Гострий зір";
    }

    @Override
    public String getDescription() {
        return "Дає нічне бачення та підсвічування на.";
    }

    @Override
    public int getSpiritualityCost() {
        return 50;
    }

    @Override
    public int getCooldown() {
        return 30;
    }

    @Override
    protected void preExecution(IAbilityContext context) {
        // Visual effect when activating
        context.spawnParticle(
                Particle.END_ROD,
                context.getCasterLocation().add(0, 1, 0),
                30,
                0.5, 0.5, 0.5
        );

        context.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // 1. Apply night vision to caster
        context.applyEffect(
                context.getCasterId(),
                PotionEffectType.NIGHT_VISION,
                DURATION_TICKS,
                0
        );

        // 2. Get all nearby living entities
        List<LivingEntity> nearbyEntities = context.getNearbyEntities(RANGE);

        if (nearbyEntities.isEmpty()) {
            context.sendMessageToCaster(
                    ChatColor.YELLOW + "Немає сутностей поблизу для підсвічування"
            );
            return AbilityResult.success();
        }

        // 3. Extract UUIDs for glowing
        List<java.util.UUID> entityIds = nearbyEntities.stream()
                .map(LivingEntity::getUniqueId)
                .collect(Collectors.toList());

        // 4. Set all entities glowing with white color
        context.setMultipleGlowing(entityIds, ChatColor.WHITE, DURATION_TICKS);

        // 5. Success message
        context.sendMessageToCaster(
                ChatColor.GREEN + "Гострий зір активовано! Підсвічено " +
                        nearbyEntities.size() + " сутностей на " + DURATION_SECONDS + " секунд."
        );

        return AbilityResult.success();
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        // Subtle sound effect
        context.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
    }
}
