package me.vangoo.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DragonScale extends ActiveAbility {

    private static final int BASE_DURATION_SECONDS = 21;
    private static final int BASE_COST = 150;
    private static final int BASE_COOLDOWN = 75;

    @Override
    public String getName() {
        return "Луска дракона";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int duration = calculateDuration(userSequence.level());
        return "Маніфестація драконячої луски. Дає Опір II та Вогнестійкість II на "
                + duration + " секунд.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                userSequence.level(),
                ScalingStrategy.WEAK
        );
        return Math.max(60, (int) (BASE_COOLDOWN / multiplier));
    }

    private int calculateDuration(int sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence,
                ScalingStrategy.MODERATE
        );
        return (int) (BASE_DURATION_SECONDS * multiplier);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Beyonder beyonder = context.getCasterBeyonder();
        int sequenceVal = beyonder.getSequence().level();

        if (sequenceVal > 6) {
            return AbilityResult.failure("✖ Ваша послідовність занизька для цієї форми");
        }

        int durationSeconds = calculateDuration(sequenceVal);
        int durationTicks = durationSeconds * 20;

        // ───── ЕФЕКТИ ─────
        context.entity().applyPotionEffect(context.getCasterId(),
                PotionEffectType.RESISTANCE,
                durationTicks,
                1
        );

        context.entity().applyPotionEffect(context.getCasterId(),
                PotionEffectType.FIRE_RESISTANCE,
                durationTicks,
                0
        );

        // ───── ЗВУК ─────
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.6f);
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.5f);
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_LAVA_POP, 0.6f, 0.8f);

        // ───── ACTIONBAR ─────
        context.messaging().sendMessageToActionBar(
                context.getCasterId(),
                Component.text("🐉 Луска дракона вкриває ваше тіло")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
        );

        // ───── ВІЗУАЛ ─────
        context.effects().spawnParticle(
                Particle.FLAME,
                context.getCasterLocation().add(0, 1, 0),
                40,
                0.6, 1.0, 0.6
        );

        context.effects().spawnParticle(
                Particle.LAVA,
                context.getCasterLocation().add(0, 1, 0),
                12,
                0.5, 1.0, 0.5
        );

        context.effects().spawnParticle(
                Particle.DRAGON_BREATH,
                context.getCasterLocation().add(0, 1.2, 0),
                25,
                0.4, 0.8, 0.4
        );

        return AbilityResult.success();
    }
}