package me.vangoo.domain.pathways.visionary.abilities;


import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    public String getDescription(Sequence userSequence) {
        int duration = scaleValue(DURATION_SECONDS, userSequence,
                SequenceScaler.ScalingStrategy.WEAK);
        return String.format("Дає нічне бачення та підсвічування на %d c.", duration);
    }

    @Override
    public int getSpiritualityCost() {
        return 50;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        int baseCooldown = 40;
        double reduction = SequenceScaler.calculateMultiplier(
                userSequence.level(),
                SequenceScaler.ScalingStrategy.STRONG
        );
        return (int) Math.ceil(baseCooldown / reduction);
    }

    @Override
    protected void preExecution(IAbilityContext context) {
        // Visual effect when activating
        context.effects().spawnParticle(
                Particle.END_ROD,
                context.getCasterLocation().add(0, 1, 0),
                30,
                0.5, 0.5, 0.5
        );

        context.effects().playSoundForPlayer(
                context.getCasterId(),
                Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence userSequence = context.getCasterBeyonder().getSequence();

        int range = scaleValue(RANGE, userSequence,
                SequenceScaler.ScalingStrategy.WEAK);
        int durationSeconds = scaleValue(DURATION_SECONDS, userSequence,
                SequenceScaler.ScalingStrategy.MODERATE);
        int durationTicks = durationSeconds * 20;

        int nightVisionAmplifier = Math.min(2, (9 - userSequence.level()) / 3);
        // 1. Apply night vision to caster
        context.entity().applyPotionEffect(
                context.getCasterId(),
                PotionEffectType.NIGHT_VISION,
                durationTicks,
                nightVisionAmplifier
        );

        // 2. Get all nearby living entities
        List<LivingEntity> nearbyEntities = context.targeting().getNearbyEntities(range);

        if (nearbyEntities.isEmpty()) {
            context.messaging().sendMessageToActionBar(context.getCasterId(),
                    Component.text("Немає сутностей поблизу для підсвічування").color(NamedTextColor.YELLOW)
            );
            return AbilityResult.success();
        }

        // 3. Extract UUIDs for glowing
        List<java.util.UUID> entityIds = nearbyEntities.stream()
                .map(LivingEntity::getUniqueId)
                .collect(Collectors.toList());

        // 4. Set all entities glowing with white color
        context.glowing().setMultipleGlowing(entityIds, ChatColor.WHITE, durationTicks);

        // 5. Success message
        context.messaging().sendMessageToActionBar(context.getCasterId(),
                Component.text("Гострий зір активовано! Підсвічено " + nearbyEntities.size() + " сутностей на " + durationSeconds + " секунд.").color(NamedTextColor.GOLD));


        return AbilityResult.success();
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        // Subtle sound effect
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
    }
}
