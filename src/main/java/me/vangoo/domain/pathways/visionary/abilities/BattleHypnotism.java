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
        Player caster = context.getCaster();
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

        applyHypnosis(context, caster, target);
        return AbilityResult.success();
    }

    private void applyHypnosis(IAbilityContext context, Player caster, Player target) {

        // ───── ВІЗУАЛ + ЗВУК ─────
        caster.getWorld().spawnParticle(
                Particle.SOUL,
                target.getEyeLocation(),
                30,
                0.4, 0.6, 0.4,
                0.01
        );

        caster.getWorld().spawnParticle(
                Particle.WITCH,
                target.getLocation().add(0, 1, 0),
                20,
                0.3, 0.5, 0.3,
                0.02
        );

        context.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.6f);
        target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f);

        // ───── ACTIONBAR ─────
        context.sendMessageToActionBar(
                caster,
                Component.text("🧠 Ви занурили ")
                        .color(NamedTextColor.DARK_PURPLE)
                        .append(Component.text(target.getName())
                                .color(NamedTextColor.LIGHT_PURPLE)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text(" у гіпноз"))
        );

        context.sendMessageToActionBar(
                target,
                Component.text("🌫 Ваша свідомість розчиняється…")
                        .color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.ITALIC)
        );

        // ───── ЕФЕКТИ ─────
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                EFFECT_DURATION_SECONDS * 20,
                1
        ));

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA,
                EFFECT_DURATION_SECONDS * 20,
                0
        ));

        // ───── ЛОГІКА ГІПНОЗУ ─────
        context.hidePlayerFromTarget(target, caster);

        context.scheduleDelayed(() -> {
            if (!target.isOnline() || !caster.isOnline()) return;

            context.showPlayerToTarget(target, caster);

            target.playSound(
                    target.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    1f,
                    1.2f
            );

            context.sendMessageToActionBar(
                    target,
                    Component.text("👁 Реальність повертається")
                            .color(NamedTextColor.YELLOW)
            );
        }, EFFECT_DURATION_SECONDS * 20L);
    }
}
