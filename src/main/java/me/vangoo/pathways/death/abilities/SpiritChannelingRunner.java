package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import me.vangoo.pathways.common.Spirits;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Разові ефекти прикликаних духів (Death, Посл. 7). Стану не тримає — тільки хореографія
 * Bukkit; що і кому коштує, вирішує {@link SpiritChanneling}. Дух із життєвим циклом
 * (Морозна тінь) сюди не поміститься — йому потрібна сесія.
 */
final class SpiritChannelingRunner {

    /** На якій висоті над медіумом висить око-споглядач. */
    private static final double EYE_HEIGHT = 12.0;
    private static final double IRIS_RADIUS = 1.6;
    /** Промінь від ока до медіума — короткий вступ, а не постійний стовп світла. */
    private static final int BEAM_TICKS = 40;
    /** Рівень Повільності в болоті довкола схопленого (0 = I), тобто Повільність II. */
    private static final int SWAMP_SLOWNESS_AMPLIFIER = 1;

    private SpiritChannelingRunner() {
    }

    /**
     * Ілюзорне око — «a Spirit World Creature with a nearly transparent eye… looking down from
     * above without blinking», телескоп медіума: усе живе довкола обводиться контуром, і бачить
     * ці контури ЛИШЕ кастер.
     *
     * <p>ponytail: скан один, у мить прикликання. Той, хто зайшов у радіус пізніше, лишиться
     * необведеним — око показує зріз, а не веде спостереження. Потрібен живий нагляд — це вже
     * сесія з власним таском, як у Морозної тіні.
     */
    static void illusoryEye(IAbilityContext context, Player caster, Sequence sequence) {
        UUID casterId = caster.getUniqueId();
        int durationTicks = SpiritMediumLore.illusoryEyeDurationSeconds(sequence) * 20;
        double radius = SpiritMediumLore.illusoryEyeRadius(sequence);
        Color deathColor = PathwayBranding.liquidOf("Death");
        Location eye = caster.getLocation().clone().add(0, EYE_HEIGHT, 0);

        context.effects().playCircleEffect(eye, IRIS_RADIUS, Particle.SOUL_FIRE_FLAME, durationTicks);
        context.effects().playGlowingDust(eye, deathColor);
        context.effects().playBeamEffect(eye, caster.getEyeLocation(), Particle.SOUL, 0.2, BEAM_TICKS);
        context.effects().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);

        int outlined = 0;
        for (LivingEntity entity : context.targeting().getNearbyEntities(radius)) {
            if (entity.getUniqueId().equals(casterId)) {
                continue;
            }
            context.glowing().setGlowing(entity.getUniqueId(), casterId,
                    PathwayBranding.textOf("Death"), durationTicks);
            outlined++;
        }

        context.messaging().sendMessage(casterId, outlined == 0
                ? "§8Око розплющилось — і не побачило нікого живого."
                : "§3👁 Око-споглядач дивиться згори: §f" + outlined + " §3живих поблизу");
    }

    /**
     * Земляний дух — «a Natural Spirit with a pair of weathered, stone-like hands… soften the
     * ground like a swamp and drag a target underground». Ціль стоїть у землі й не рухається,
     * довкола розповзається болото.
     *
     * <p>ponytail: занурення — візуальне (руки, болото, сліпота «під землею»), блоків світу
     * дух не чіпає. Справжнє закопування вимагало б підміни блоків із відновленням і
     * задухи-виїмки; захочеться — це окрема сесія, а не два рядки тут.
     */
    static void earthSpirit(IAbilityContext context, Player caster, LivingEntity target,
                            Sequence sequence) {
        UUID targetId = target.getUniqueId();
        int holdTicks = SpiritMediumLore.earthHoldSeconds(sequence) * 20;
        Color deathColor = PathwayBranding.liquidOf("Death");
        Location feet = target.getLocation();

        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, holdTicks, 255);
        context.entity().applyPotionEffect(targetId, PotionEffectType.JUMP_BOOST, holdTicks, 250);
        context.entity().applyPotionEffect(targetId, PotionEffectType.BLINDNESS, holdTicks, 0);

        context.effects().playGraspingHands(feet, deathColor, holdTicks);
        context.effects().playWaveEffect(feet, SpiritMediumLore.EARTH_SWAMP_RADIUS,
                Particle.DUST_PLUME, holdTicks);
        context.effects().playSound(feet, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.6f);
        context.effects().playSound(feet, Sound.BLOCK_MUD_PLACE, 1.0f, 0.5f);

        int mired = 0;
        for (Entity nearby : target.getNearbyEntities(SpiritMediumLore.EARTH_SWAMP_RADIUS,
                SpiritMediumLore.EARTH_SWAMP_RADIUS, SpiritMediumLore.EARTH_SWAMP_RADIUS)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(caster)
                    || Spirits.isSpirit(living)) {
                continue;
            }
            context.entity().applyPotionEffect(living.getUniqueId(), PotionEffectType.SLOWNESS,
                    holdTicks, SWAMP_SLOWNESS_AMPLIFIER);
            mired++;
        }

        if (target instanceof Player victim) {
            victim.sendMessage("§2Земля розм'якла під ногами й зімкнулась — ви не можете рушити!");
        }
        context.messaging().sendMessage(caster.getUniqueId(),
                "§2🖐 Земляний дух схопив §f" + target.getName() + "§2 на "
                        + SpiritMediumLore.earthHoldSeconds(sequence) + " с"
                        + (mired > 0 ? " §8(у болоті ще " + mired + ")" : ""));
    }
}
