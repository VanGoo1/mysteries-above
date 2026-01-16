package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.UUID;

public class BattleHypnotism extends ActiveAbility {

    private static final int BASE_COST = 100;
    private static final int BASE_RANGE = 15;
    private static final int BASE_COOLDOWN = 25;
    private static final int EFFECT_DURATION_SECONDS = 10;

    @Override
    public String getName() {
        return "Бойовий гіпноз";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Змушує ворога втратити вас з поля зору, затуманюючи його свідомість.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                userSequence.level(),
                SequenceScaler.ScalingStrategy.MODERATE
        );
        return Math.max(5, (int) (BASE_COOLDOWN / multiplier));
    }

    private int getRange(int sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence,
                SequenceScaler.ScalingStrategy.STRONG
        );
        return (int) (BASE_RANGE * multiplier);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        Beyonder beyonder = context.getCasterBeyonder();

        int range = getRange(beyonder.getSequence().level());

        RayTraceResult rayTrace = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof Player && entity != caster
        );

        if (rayTrace == null || !(rayTrace.getHitEntity() instanceof Player target)) {

            return AbilityResult.failure("✖ Ви не дивитесь на живу ціль");
        }

        applyHypnosis(context, caster.getUniqueId(), target);
        return AbilityResult.success();
    }

    private void applyHypnosis(IAbilityContext context, UUID casterId, Player target) {

        // ───── ВІЗУАЛ + ЗВУК ─────
        context.effects().spawnParticle(
                Particle.SOUL,
                target.getEyeLocation(),
                30,
                0.4, 0.6, 0.4
        );

        context.effects().spawnParticle(
                Particle.WITCH,
                target.getLocation().add(0, 1, 0),
                20,
                0.3, 0.5, 0.3
        );

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.6f);
        context.effects().playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f);

        // ───── ACTIONBAR ─────
        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("🧠 Ви занурили ")
                        .color(NamedTextColor.DARK_PURPLE)
                        .append(Component.text(target.getName())
                                .color(NamedTextColor.LIGHT_PURPLE)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text(" у гіпноз"))
        );

        context.messaging().sendMessageToActionBar(
                target.getUniqueId(),
                Component.text("🌫 Ваша свідомість розчиняється…")
                        .color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.ITALIC)
        );

        // ───── ЕФЕКТИ ─────
        context.entity().applyPotionEffect(target.getUniqueId(),
                PotionEffectType.SLOWNESS,
                EFFECT_DURATION_SECONDS * 20,
                1
        );

        context.entity().applyPotionEffect(target.getUniqueId(),
                PotionEffectType.NAUSEA,
                EFFECT_DURATION_SECONDS * 20,
                0
        );

        // ───── ЛОГІКА ГІПНОЗУ ─────
        context.entity().hidePlayerFromTarget(target.getUniqueId(), casterId);

        context.scheduling().scheduleDelayed(() -> {
            if (!context.playerData().isOnline(target.getUniqueId()) || !context.playerData().isOnline(casterId))
                return;

            context.entity().showPlayerToTarget(target.getUniqueId(), casterId);

            context.effects().playSound(
                    target.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    1f,
                    1.2f
            );

            context.messaging().sendMessageToActionBar(
                    target.getUniqueId(),
                    Component.text("👁 Реальність повертається")
                            .color(NamedTextColor.YELLOW)
            );
        }, EFFECT_DURATION_SECONDS * 20L);
    }
}
