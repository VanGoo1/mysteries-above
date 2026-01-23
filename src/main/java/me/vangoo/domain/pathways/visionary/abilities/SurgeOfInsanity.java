package me.vangoo.domain.pathways.visionary.abilities;

import org.bukkit.Color;
import org.bukkit.Particle;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class SurgeOfInsanity extends ActiveAbility {

    private static final int BASE_RANGE = 5;
    private static final int COOLDOWN = 30;
    private static final int BASE_DURATION_SECONDS = 10;
    private static final int BASE_SANITY_INCREASE = 5;

    private static final double MOB_DAMAGE = 8.0; // 4 серця

    @Override
    public String getName() {
        return "Всплеск божевілля";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int range = scaleValue(BASE_RANGE, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        int duration = scaleValue(BASE_DURATION_SECONDS, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        int sanity = scaleValue(BASE_SANITY_INCREASE, userSequence, SequenceScaler.ScalingStrategy.WEAK);

        return String.format(
                "У радіусі %d блоків накладає Сліпоту та Слабкість на %d с. " +
                        "Потойбічні втрачають %d Sanity. Інші істоти отримують 4 серця шкоди.",
                range, duration, sanity
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence sequence = context.getCasterBeyonder().getSequence();

        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int durationSeconds = scaleValue(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int sanityLoss = scaleValue(BASE_SANITY_INCREASE, sequence, SequenceScaler.ScalingStrategy.WEAK);
        int durationTicks = durationSeconds * 20;

        // --- РЕСУРСИ ---
        if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
            context.messaging().sendMessageToActionBar(context.getCasterId(),
                    Component.text(
                            ChatColor.RED + "✗ Недостатньо духовності"
                    ));
            return AbilityResult.failure("No spirituality");
        }

        context.events().publishAbilityUsedEvent(this, context.getCasterBeyonder());

        Location loc = context.getCasterLocation();

        // --- ВІЗУАЛИ ---
        context.effects().playSphereEffect(loc, range, Particle.WITCH, Math.min(40, durationTicks));
        context.effects().playWaveEffect(loc, range, Particle.SMOKE, durationTicks);

        context.effects().playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.6f);
        context.effects().playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.5f);

        List<LivingEntity> targets = context.targeting().getNearbyEntities(range);

        if (targets.isEmpty()) {
            context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text(
                    ChatColor.GRAY + "✦ Поблизу немає цілей"
            ));
            return AbilityResult.failure("No targets");
        }

        int players = 0;
        int mobs = 0;

        for (LivingEntity target : targets) {
            if (target.getUniqueId().equals(context.getCasterId())) continue;

            try {
                context.effects().playTrailEffect(target.getUniqueId(), Particle.CRIT, Math.min(60, durationTicks));
            } catch (Exception ignored) {
            }

            if (target instanceof Player) {
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.WEAKNESS, durationTicks, 0);
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.BLINDNESS, durationTicks, 0);
                context.beyonder().updateSanityLoss(target.getUniqueId(), sanityLoss);

                context.messaging().sendMessageToActionBar(
                        target.getUniqueId(),
                        Component.text(
                                ChatColor.DARK_PURPLE +
                                        "🌀 Розум затьмарюється  +" + sanityLoss + " Sanity"
                        )
                );

                context.effects().spawnParticle(
                        Particle.SMOKE,
                        target.getLocation().add(0, 1.7, 0),
                        12, 0.2, 0.2, 0.2
                );

                players++;
            } else {
                context.entity().damage(target.getUniqueId(), MOB_DAMAGE);

                context.effects().spawnParticle(
                        Particle.CRIT,
                        target.getLocation().add(0, 1, 0),
                        12, 0.2, 0.2, 0.2
                );

                mobs++;
            }
        }

        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.PURPLE, 1.5f);

        context.effects().playExplosionRingEffect(
                loc,
                Math.max(3.0, range / 1.5),
                Particle.DUST,
                dustOptions
        );


        // --- ФІНАЛЬНИЙ ACTION BAR ДЛЯ КАСТЕРА ---
        context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text(
                ChatColor.DARK_PURPLE + "🌀 Божевілля вивільнено  " +
                        ChatColor.GRAY + "Гравців: " + players +
                        " | Істот: " + mobs
        ));

        return AbilityResult.success();
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }
}
