package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Magician — Damage Transfer (Перенос шкоди)
 *
 * The Magician "pulls" a wound from one place and "places" it onto the target.
 * Transfers recently received damage from caster to target, healing self.
 */
public class DamageTransfer extends ActiveAbility {

    private static final int BASE_COST = 100;
    private static final int BASE_COOLDOWN = 45;
    private static final double MAX_RANGE = 8.0;
    private static final double MAX_TRANSFER = 10.0;
    private static final long DAMAGE_WINDOW_MS = 10_000; // 10 seconds

    // Track recent damage: casterId -> (timestamp, amount)
    private static final Map<UUID, DamageRecord> recentDamage = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Перенос шкоди";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Переносить шкоду, отриману впродовж останніх " + (DAMAGE_WINDOW_MS / 1000) +
                "с, на ціль (макс " + (int) MAX_TRANSFER + " HP). Ви зцілюєте цю кількість.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(MAX_RANGE);
    }

    /**
     * Called externally to record damage taken by a Fool pathway beyonder.
     * Should be integrated into the damage handling pipeline.
     */
    public static void recordDamage(UUID playerId, double amount) {
        DamageRecord existing = recentDamage.get(playerId);
        long now = System.currentTimeMillis();

        if (existing != null && (now - existing.timestamp) < DAMAGE_WINDOW_MS) {
            // Accumulate within window
            existing.amount = Math.min(MAX_TRANSFER, existing.amount + amount);
            existing.timestamp = now;
        } else {
            recentDamage.put(playerId, new DamageRecord(now, Math.min(MAX_TRANSFER, amount)));
        }
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Check for recent damage
        DamageRecord record = recentDamage.get(casterId);
        long now = System.currentTimeMillis();

        if (record == null || (now - record.timestamp) > DAMAGE_WINDOW_MS || record.amount <= 0) {
            return AbilityResult.failure("Ви не отримували шкоди впродовж останніх " +
                    (DAMAGE_WINDOW_MS / 1000) + " секунд");
        }

        // Find target
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі в радіусі " + (int) MAX_RANGE + " блоків");
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();
        double transferAmount = record.amount;

        // Consume the damage record
        recentDamage.remove(casterId);

        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        Location targetLoc = target.getLocation();

        // Animation: damage "flowing" from caster to target
        playTransferEffect(context, casterLoc, targetLoc);

        // Apply after animation delay
        context.scheduling().scheduleDelayed(() -> {
            // Heal caster
            context.entity().heal(casterId, transferAmount);

            // Damage target
            context.entity().damage(targetId, transferAmount);

            // Impact effects
            context.effects().spawnParticle(Particle.DAMAGE_INDICATOR,
                    targetLoc.clone().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
            context.effects().playSound(targetLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.8f);

            // Heal effect on caster
            Location healLoc = context.playerData().getCurrentLocation(casterId);
            if (healLoc != null) {
                context.effects().spawnParticle(Particle.HEART, healLoc.clone().add(0, 2, 0), 5, 0.2, 0.1, 0.2);
            }

            context.messaging().sendMessage(casterId,
                    ChatColor.GREEN + "✦ Перенесено " + ChatColor.WHITE +
                            String.format("%.1f", transferAmount) + ChatColor.GREEN + " шкоди на " +
                            (target instanceof Player p ? p.getName() : target.getType().name()));

            if (target instanceof Player targetPlayer) {
                context.messaging().sendMessage(targetId,
                        ChatColor.RED + "✦ Чужа рана передалась вам! (-" +
                                String.format("%.1f", transferAmount) + " HP)");
            }
        }, 15L); // 0.75 second delay for animation

        return AbilityResult.success();
    }

    private void playTransferEffect(IAbilityContext context, Location from, Location to) {
        if (from == null || to == null) return;

        // Animated line of DAMAGE_INDICATOR particles from caster to target
        for (int i = 0; i < 15; i++) {
            int tick = i;
            context.scheduling().scheduleDelayed(() -> {
                double progress = tick / 15.0;
                double x = from.getX() + (to.getX() - from.getX()) * progress;
                double y = (from.getY() + 1) + (to.getY() + 1 - from.getY() - 1) * progress;
                double z = from.getZ() + (to.getZ() - from.getZ()) * progress;

                Location point = new Location(from.getWorld(), x, y, z);

                Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(200, 30, 30), 1.0f);
                from.getWorld().spawnParticle(Particle.DUST, point, 3, 0.05, 0.05, 0.05, 0, redDust);
                context.effects().spawnParticle(Particle.DAMAGE_INDICATOR, point, 1, 0, 0, 0);
            }, tick);
        }

        context.effects().playSound(from, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1f, 1.2f);
    }

    private static class DamageRecord {
        long timestamp;
        double amount;

        DamageRecord(long timestamp, double amount) {
            this.timestamp = timestamp;
            this.amount = amount;
        }
    }

    @Override
    public void cleanUp() {
        recentDamage.clear();
    }
}
