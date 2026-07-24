package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.UUID;

/**
 * Sequence 7: Вогонь Світла. Вікі: щільне золоте святе полум'я в цільовій точці, що виглядає
 * як море вогню й несе велич Сонця. Purification/Exorcism не універсальні — "темні" pathway
 * (по {@link HolyAffinity}) отримують повний множник, звичайна нежить — знижений (той самий
 * розподіл, що й в інших Sun-здібностях цього файлу).
 */
public class FireOfLight extends ActiveAbility {

    private static final double MAX_RANGE = 18.0;
    private static final double RADIUS = 4.5;
    private static final int BASE_DAMAGE = 12;
    private static final int FIRE_TICKS = 140; // довше за звичайний підпал — "море вогню"
    private static final int WEAKNESS_TICKS = 100;
    private static final int COOLDOWN = 30;

    @Override
    public String getName() {
        return "Вогонь Світла";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG);
        return String.format(
                "§fВивільняє щільне золоте святе полум'я в цільовій точці (радіус §e%.0f §fбл) — " +
                        "море вогню, що несе велич Сонця. §7Шкода: §c%d §7(повна по темних pathway, знижена по нежиті).",
                RADIUS, damage
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 60;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Location center = resolveTargetLocation(context);
        if (center == null) {
            return AbilityResult.failure("Немає цілі для Вогню Світла");
        }

        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int baseDamage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG);

        int hit = 0;
        if (center.getWorld() != null) {
            for (Entity entity : center.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (living.getUniqueId().equals(casterId)) continue;

                boolean darkTarget = isDarkPathway(context, living);
                double multiplier = HolyAffinity.damageMultiplier(darkTarget || HolyTargetClassifier.isDarkOrUndead(living, context))
                        * context.amplification().getDamageMultiplier(casterId);
                int damage = (int) Math.ceil(baseDamage * multiplier);

                context.entity().damage(living.getUniqueId(), damage);
                context.entity().applyPotionEffect(living.getUniqueId(), PotionEffectType.WEAKNESS, WEAKNESS_TICKS, 0);
                living.setFireTicks(FIRE_TICKS);
                hit++;
            }
        }

        playFireSeaEffect(context, center);
        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "☀ Вогонь Світла спопелив цілей: " + hit);

        return AbilityResult.success();
    }

    private Location resolveTargetLocation(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targetOpt.isPresent()) return targetOpt.get().getLocation();

        Player caster = context.getCasterPlayer();
        var rayTrace = caster.getWorld().rayTraceBlocks(caster.getEyeLocation(), caster.getEyeLocation().getDirection(), MAX_RANGE);
        return rayTrace != null ? rayTrace.getHitPosition().toLocation(caster.getWorld()) : null;
    }

    private boolean isDarkPathway(IAbilityContext context, LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        var beyonder = context.beyonder().getBeyonder(player.getUniqueId());
        return beyonder != null && HolyAffinity.isDark(beyonder.getPathway().getName());
    }

    private void playFireSeaEffect(IAbilityContext context, Location center) {
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 180, 0), 1.8f);

        context.effects().playSphereEffect(center, RADIUS * 0.7, Particle.FLAME, 30);
        context.effects().playCircleEffect(center, RADIUS, Particle.FLAME, 30);
        context.effects().playExplosionRingEffect(center.clone().add(0, 0.2, 0), RADIUS, Particle.DUST, gold);
        context.effects().playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        context.effects().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
        context.effects().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.3f);
    }
}
