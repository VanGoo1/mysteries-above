package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WhipOfPain extends ActiveAbility {

    private static final double RANGE = 10.0;
    private static final double DAMAGE = 7.0;

    @Override
    public String getName() {
        return "Хлист Болю";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Матеріалізує електричний батіг. Завдає болю та примушує жертву розкрити свої секрети (Ендер-скриня, Рівень сили).";
    }

    @Override
    public int getSpiritualityCost() {
        return 60;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 12;
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(RANGE);

        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі.");
        }

        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        Location casterEye = context.playerData().getEyeLocation(casterId);
        Location targetEye = context.playerData().getEyeLocation(targetId);

        if (casterEye != null && targetEye != null) {
            context.effects().playBeamEffect(casterEye.add(0, -0.2, 0), targetEye, Particle.CRIT, 0.2, 5);
            context.effects().spawnParticle(Particle.ELECTRIC_SPARK, targetEye, 15, 0.5, 0.5, 0.5);
        }

        Location targetLoc = context.playerData().getCurrentLocation(targetId);
        if (targetLoc != null) {
            context.effects().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
        }

        context.entity().damage(targetId, DAMAGE);
        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, 60, 4);
        context.entity().applyPotionEffect(targetId, PotionEffectType.BLINDNESS, 40, 0);

        revealDeepSecrets(context, casterId, targetId);

        if (context.playerData().isOnline(targetId)) {
            context.messaging().sendMessage(targetId, ChatColor.RED + "Ви не можете стримати правду... Біль змушує вас говорити!");
        }

        return AbilityResult.success();
    }

    private void revealDeepSecrets(IAbilityContext context, UUID casterId, UUID targetId) {
        context.messaging().sendMessage(casterId, ChatColor.DARK_AQUA + "▬▬▬▬ ПРИМУСОВИЙ ДОПИТ ▬▬▬▬");

        String targetName = context.playerData().getName(targetId);
        double hp = context.playerData().getHealth(targetId);

        context.messaging().sendMessage(casterId, ChatColor.GRAY + "Ціль: " + ChatColor.RED + targetName +
                ChatColor.GRAY + " | HP: " + ChatColor.YELLOW + String.format("%.1f", hp));

        if (context.playerData().isOnline(targetId)) {
            Optional<Integer> seqLevel = Optional.empty();
            if (context.beyonder().isBeyonder(targetId)) {
                var beyonder = context.beyonder().getBeyonder(targetId);
                if (beyonder != null) {
                    seqLevel = Optional.of(beyonder.getSequenceLevel());
                }
            }

            if (seqLevel.isPresent()) {
                context.messaging().sendMessage(casterId, ChatColor.GRAY + "Статус: " + ChatColor.LIGHT_PURPLE + "Потойбічний (Seq " + seqLevel.get() + ")");
            } else {
                context.messaging().sendMessage(casterId, ChatColor.GRAY + "Статус: " + ChatColor.GREEN + "Звичайна людина");
            }

            String heldItem = context.playerData().getMainHandItemName(targetId);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "Зброя: " + ChatColor.WHITE + heldItem);

            List<String> secretStash = context.playerData().getEnderChestContents(targetId, 5);

            if (!secretStash.isEmpty()) {
                context.messaging().sendMessage(casterId, ChatColor.GOLD + ">> Потаємні думки (Ender Chest):");
                for (String item : secretStash) {
                    context.messaging().sendMessage(casterId, ChatColor.DARK_GRAY + " - " + ChatColor.ITALIC + item);
                }
            } else {
                context.messaging().sendMessage(casterId, ChatColor.GOLD + ">> Потаємні думки: " + ChatColor.GRAY + "Пусто/Чисто");
            }
        }

        context.messaging().sendMessage(casterId, ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}