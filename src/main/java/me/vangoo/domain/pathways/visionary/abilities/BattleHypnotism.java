package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

public class BattleHypnotism extends ActiveAbility {

    private static final int BASE_COST = 50;
    private static final int BASE_RANGE = 15;
    private static final int BASE_COOLDOWN = 25;
    private static final int EFFECT_DURATION_SECONDS = 10;

    @Override
    public String getName() {
        return "Бойовий гіпноз";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Загіпнотизуйте ворога і він втратить вас з поля зору";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        double multiplier = SequenceScaler.calculateMultiplier(userSequence.level(), SequenceScaler.ScalingStrategy.MODERATE);
        int cooldown = (int) (BASE_COOLDOWN / multiplier);
        return Math.max(5, cooldown);
    }

    private int getRange(int sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(sequence, SequenceScaler.ScalingStrategy.STRONG);
        return (int) (BASE_RANGE * multiplier);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        Beyonder beyonder = context.getCasterBeyonder();
        int sequenceVal = beyonder.getSequence().level();

        Spirituality sp = beyonder.getSpirituality();
        if (sp.current() < BASE_COST) {
            return AbilityResult.failure("Недостатньо духовності.");
        }

        int range = getRange(sequenceVal);
        RayTraceResult rayTrace = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof Player && !entity.getUniqueId().equals(caster.getUniqueId())
        );

        if (rayTrace == null || rayTrace.getHitEntity() == null) {
            return AbilityResult.failure("Ви не дивитесь на ціль.");
        }

        if (!(rayTrace.getHitEntity() instanceof Player target)) {
            return AbilityResult.failure("Тільки гравці мають свідомість для гіпнозу.");
        }

        applyHypnosis(context, caster, target);

        beyonder.setSpirituality(new Spirituality(sp.current() - BASE_COST, sp.maximum()));

        return AbilityResult.success();
    }

    private void applyHypnosis(IAbilityContext context, Player caster, Player target) {
        // Візуал та звук
        context.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.5f);
        target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f);
        caster.spawnParticle(Particle.SOUL, target.getEyeLocation(), 20, 0.5, 0.5, 0.5, 0);

        caster.sendMessage(ChatColor.GREEN + "Ви загіпнотизували " + target.getName() + "!");
        target.sendMessage(ChatColor.DARK_PURPLE + "Ваша свідомість затуманюється...");

        // Ефекти
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_DURATION_SECONDS * 20, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, EFFECT_DURATION_SECONDS * 20, 0));

        // --- DDD FIX: Використовуємо методи контексту ---
        // Замість прямого виклику Bukkit API з плагіном, ми кажемо контексту: "сховай мене від нього"
        context.hidePlayerFromTarget(target, caster);

        // Таймер через контекст
        context.scheduleDelayed(() -> {
            if (target.isOnline() && caster.isOnline()) {
                // Відновлення видимості через контекст
                context.showPlayerToTarget(target, caster);

                target.sendMessage(ChatColor.YELLOW + "Ви знову бачите реальність.");
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }, EFFECT_DURATION_SECONDS * 20L);
    }
}
