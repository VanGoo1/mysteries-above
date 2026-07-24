package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.UUID;

public class Sunshine extends ActiveAbility {

    private static final double MAX_RANGE = 15.0;
    private static final int BASE_DAMAGE = 8;
    private static final int FIRE_TICKS = 60; // 3 секунди
    private static final int BLIND_TICKS = 60; // 3 секунди
    private static final int COOLDOWN = 10;

    @Override
    public String getName() {
        return "Сонячне сяйво";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG);
        return String.format(
                "§fПромінь пекучого світла б'є в ціль на відстані до %.0f бл, засліплюючи її та підпалюючи. " +
                        "§7Шкода: §c%d §7(проти темних/нежиті — повна, проти інших — знижена).",
                MAX_RANGE, damage
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 35;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі в промені світла");
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Color sunlight = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());

        int baseDamage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG);

        // Промінь тягнеться до цілі; шкода лягає в мить влучання (onArrival).
        context.effects().playSound(context.getCasterEyeLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        context.effects().playTravelingBeam(context.getCasterEyeLocation(), target.getEyeLocation(), sunlight,
                () -> applyImpact(context, target, targetId, casterId, baseDamage, sunlight));

        return AbilityResult.success();
    }

    private void applyImpact(IAbilityContext context, LivingEntity target, UUID targetId, UUID casterId,
                             int baseDamage, Color sunlight) {
        if (!target.isValid()) return;

        boolean darkTarget = HolyTargetClassifier.isDarkOrUndead(target, context);
        double multiplier = HolyAffinity.damageMultiplier(darkTarget) * context.amplification().getDamageMultiplier(casterId);
        int damage = (int) Math.ceil(baseDamage * multiplier);

        context.entity().damage(targetId, damage);
        context.entity().applyPotionEffect(targetId, PotionEffectType.BLINDNESS, BLIND_TICKS, 0);
        target.setFireTicks(FIRE_TICKS);

        Location impact = target.getLocation().add(0, 1, 0);
        context.effects().playGlowingDust(impact, sunlight);
        context.effects().playSound(impact, Sound.ITEM_TOTEM_USE, 0.5f, 1.7f);

        context.messaging().sendMessage(casterId, ChatColor.GOLD + "☀ Сонячне сяйво вразило ціль на "
                + damage + (darkTarget ? " (повна кара темряві!)" : ""));
    }
}
