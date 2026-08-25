package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SwindlerInfluence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Посл. 8: «Ментальний розлад» — ціль отримує чужу, хибну реальність.
 *
 * <p>Нуль шкоди: гравець бачить стіни, яких немає, і чує кроки за спиною; створіння
 * сліпне і губить ціль. Ілюзія персональна — світ довкола не змінюється, а всі
 * фальшиві блоки повертає власний таск ефекту.
 */
public class MentalDisruption extends ActiveAbility {

    /** Період фантомних звуків (тіки) — рідше, ніж бачить око, щоб лякало, а не гуділо. */
    private static final long WHISPER_PERIOD_TICKS = 25L;
    private static final int PHANTOM_BLOCK_COUNT = 14;
    private static final double PHANTOM_RADIUS = 5.0;

    private static final Sound[] WHISPERS = {
            Sound.ENTITY_ENDERMAN_STARE,
            Sound.BLOCK_STONE_STEP,
            Sound.ENTITY_ZOMBIE_AMBIENT
    };
    private static final float[] WHISPER_VOLUME = {0.5f, 0.6f, 0.4f};
    private static final float[] WHISPER_PITCH = {0.7f, 0.8f, 0.5f};

    @Override
    public String getName() {
        return "Ментальний розлад";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви підміняєте чуття цілі в радіусі " + (int) SwindlerInfluence.DISRUPTION_RANGE
                + " блоків на " + SwindlerInfluence.disruptionDurationTicks(userSequence) / 20
                + " с: гравець бачить стіни, яких немає, і чує кроки за спиною; створіння сліпне. "
                + "Потойбічні втрачають " + SwindlerInfluence.disruptionSanityLoss(userSequence) + " глузду.";
    }

    @Override
    public int getSpiritualityCost() {
        return SwindlerInfluence.DISRUPTION_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SwindlerInfluence.DISRUPTION_COOLDOWN_SECONDS;
    }

    @Override
    protected Optional<UUID> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(SwindlerInfluence.DISRUPTION_RANGE).map(LivingEntity::getUniqueId);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(SwindlerInfluence.DISRUPTION_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("✖ Ви не дивитесь на живу ціль");
        }

        LivingEntity target = targetOpt.get();
        Beyonder caster = context.getCasterBeyonder();
        int duration = SwindlerInfluence.disruptionDurationTicks(caster.getSequence());

        if (target instanceof Player victim) {
            disruptPlayer(context, victim, duration);
        } else {
            // Створіння не має клієнта, якому можна збрехати — його хибна реальність це сліпота.
            context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.BLINDNESS, duration, 0);
            if (target instanceof Mob mob) mob.setTarget(null);
        }

        Beyonder victimBeyonder = context.beyonder().getBeyonder(target.getUniqueId());
        if (victimBeyonder != null) {
            context.beyonder().updateSanityLoss(target.getUniqueId(),
                    SwindlerInfluence.disruptionSanityLoss(caster.getSequence()));
        }

        context.effects().playSound(context.getCasterLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.6f);
        context.messaging().sendMessageToActionBar(context.getCasterId(),
                Component.text("🌀 Реальність цілі підмінено").color(NamedTextColor.DARK_PURPLE));
        return AbilityResult.success();
    }

    /** Персональна фальшива реальність: стіни-примари, шепіт з випадкових боків, дим перед очима. */
    private void disruptPlayer(IAbilityContext context, Player victim, int duration) {
        UUID victimId = victim.getUniqueId();

        context.effects().playPhantomBlocks(victimId, victim.getLocation(),
                Material.STONE, PHANTOM_BLOCK_COUNT, PHANTOM_RADIUS, duration);
        context.effects().spawnParticleForPlayer(victimId, Particle.WITCH,
                victim.getEyeLocation(), 40, 0.8, 0.8, 0.8);
        context.messaging().sendMessageToActionBar(victimId,
                Component.text("🌀 Ви більше не певні, що бачите…").color(NamedTextColor.DARK_PURPLE));

        BukkitTask[] holder = new BukkitTask[1];
        long[] elapsed = {0L};
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            elapsed[0] += WHISPER_PERIOD_TICKS;
            if (elapsed[0] > duration) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            Player p = victim.isOnline() ? victim : null;
            if (p == null) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            int i = ThreadLocalRandom.current().nextInt(WHISPERS.length);
            Location from = p.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-4, 4),
                    ThreadLocalRandom.current().nextDouble(-1, 2),
                    ThreadLocalRandom.current().nextDouble(-4, 4));
            p.playSound(from, WHISPERS[i], WHISPER_VOLUME[i], WHISPER_PITCH[i]);
            context.effects().spawnParticleForPlayer(victimId, Particle.SMOKE, from, 8, 0.2, 0.2, 0.2);
        }, WHISPER_PERIOD_TICKS, WHISPER_PERIOD_TICKS);
    }
}
