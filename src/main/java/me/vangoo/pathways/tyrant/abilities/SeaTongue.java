package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.WaterMob;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Sequence 5: Мова Моря. Океанський Співець говорить із мешканцями глибин — вони чують
 * і йдуть за ним: морська живність поблизу підсилюється й кидається на вашу ціль, а всі
 * вороги поруч проступають крізь товщу води (світіння лише для кастера).
 */
public class SeaTongue extends ActiveAbility {

    private static final int BASE_RADIUS = 16;
    private static final int BASE_DURATION_SECONDS = 20;
    private static final int SIGHT_RANGE = 25;
    private static final int COOLDOWN = 30;
    private static final int SPIRITUALITY_COST = 50;

    @Override
    public String getName() {
        return "Мова Моря";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return String.format(
                "§fВи говорите мовою глибин: морська живність у радіусі §b%d §fблоків на §b%d §fсекунд " +
                        "стає на ваш бік — підсилена силою та швидкістю, кидається на вашу ціль.\n" +
                        "Водночас усіх ворогів поруч видно крізь воду й стіни — видно лише вам.",
                radius(sequence), duration(sequence));
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int radius = radius(sequence);
        int durationTicks = duration(sequence) * 20;
        UUID casterId = context.getCasterId();

        LivingEntity prey = context.targeting().getTargetedEntity(SIGHT_RANGE).orElse(null);
        int allies = 0;
        for (LivingEntity entity : context.targeting().getNearbyEntities(radius)) {
            if (entity.getUniqueId().equals(casterId)) continue;

            if (isAquatic(entity)) {
                context.entity().applyPotionEffect(entity.getUniqueId(), PotionEffectType.STRENGTH, durationTicks, 0);
                context.entity().applyPotionEffect(entity.getUniqueId(), PotionEffectType.SPEED, durationTicks, 1);
                if (entity instanceof Mob mob) {
                    // ponytail: наведення разове на каст; тікаючий реєстр цілей — лише якщо
                    // в грі виявиться, що моби надто швидко перемикаються назад.
                    mob.setTarget(prey);
                }
                allies++;
            } else if (entity instanceof Monster || entity instanceof Player) {
                context.glowing().setGlowing(entity.getUniqueId(), casterId,
                        PathwayBranding.textOf("Tyrant"), durationTicks);
            }
        }

        Location center = player.getLocation();
        var color = PathwayBranding.liquidOf("Tyrant");
        context.effects().playWaveEffect(center, radius * 0.6, Particle.BUBBLE, 40);
        context.effects().playRisingSpiral(player.getEyeLocation(), 2.0, 1.2, color, 40);
        context.effects().playSound(center, Sound.ENTITY_DOLPHIN_PLAY, 1.4f, 0.8f);
        context.effects().playSound(center, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 1.2f, 1.0f);

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("Глибина відгукнулась: союзників — " + allies, NamedTextColor.AQUA));
        return AbilityResult.success();
    }

    /** Морські мешканці: риби/кальмари/дельфіни (WaterMob) + черепахи, аксолотлі, потопельники, вартові. */
    private boolean isAquatic(LivingEntity entity) {
        return entity instanceof WaterMob
                || entity instanceof Turtle
                || entity instanceof Axolotl
                || entity instanceof Drowned
                || entity instanceof Guardian;
    }

    private int radius(Sequence sequence) {
        return scaleValue(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
    }

    private int duration(Sequence sequence) {
        return scaleValue(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
    }
}
