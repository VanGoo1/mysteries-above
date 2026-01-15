package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;

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

    /**
     * Цей метод повідомляє батьківському класу Ability, кого ми намагаємось атакувати.
     * Це вмикає автоматичну перевірку різниці рівнів (Sequence Suppression).
     * Якщо жертва має значно вищий рівень (меншу цифру Seq), здібність може не спрацювати.
     */
    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        // Ми повертаємо ту саму ціль, яку шукаємо для виконання.
        // Це дозволяє базовому класу перевірити "Caster Seq vs Target Seq" ПЕРЕД виконанням performExecution.
        return context.getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();

        // Тут ми знову шукаємо ціль, бо логіка перевірки та виконання розділена.
        // Якщо ми дійшли сюди, значить перевірка на Послідовність (Sequence Check) вже пройшла успішно.
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(RANGE);

        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі.");
        }

        LivingEntity target = targetOpt.get();

        // --- ВІЗУАЛ ТА ЕФЕКТИ ---
        context.playBeamEffect(caster.getEyeLocation().add(0, -0.2, 0), target.getEyeLocation(), Particle.CRIT, 0.2, 5);
        context.spawnParticle(Particle.ELECTRIC_SPARK, target.getEyeLocation(), 15, 0.5, 0.5, 0.5);
        context.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);

        context.damage(target.getUniqueId(), DAMAGE);
        context.applyEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, 60, 4);
        context.applyEffect(target.getUniqueId(), PotionEffectType.BLINDNESS, 40, 0);

        // --- ГЛИБОКИЙ ДОПИТ ---
        revealDeepSecrets(context, caster, target);

        if (target instanceof Player) {
            context.sendMessage(target.getUniqueId(), ChatColor.RED + "Ви не можете стримати правду... Біль змушує вас говорити!");
        }

        return AbilityResult.success();
    }

    private void revealDeepSecrets(IAbilityContext context, Player caster, LivingEntity target) {
        context.sendMessageToCaster(ChatColor.DARK_AQUA + "▬▬▬▬ ПРИМУСОВИЙ ДОПИТ ▬▬▬▬");

        String targetName = target.getName();
        double hp = target.getHealth();
        context.sendMessageToCaster(ChatColor.GRAY + "Ціль: " + ChatColor.RED + targetName +
                ChatColor.GRAY + " | HP: " + ChatColor.YELLOW + String.format("%.1f", hp));

        if (target instanceof Player) {
            Player pTarget = (Player) target;

            Optional<Integer> seqLevel = context.getEntitySequenceLevel(pTarget.getUniqueId());
            if (seqLevel.isPresent()) {
                context.sendMessageToCaster(ChatColor.GRAY + "Статус: " + ChatColor.LIGHT_PURPLE + "Потойбічний (Seq " + seqLevel.get() + ")");
            } else {
                context.sendMessageToCaster(ChatColor.GRAY + "Статус: " + ChatColor.GREEN + "Звичайна людина");
            }

            String heldItem = context.playerData().getMainHandItemName((pTarget.getUniqueId()));
            context.sendMessageToCaster(ChatColor.GRAY + "Зброя: " + ChatColor.WHITE + heldItem);

            List<String> secretStash = context.getEnderChestContents(pTarget.getUniqueId(), 5);

            if (!secretStash.isEmpty()) {
                context.sendMessageToCaster(ChatColor.GOLD + ">> Потаємні думки (Ender Chest):");
                for (String item : secretStash) {
                    context.sendMessageToCaster(ChatColor.DARK_GRAY + " - " + ChatColor.ITALIC + item);
                }
            } else {
                context.sendMessageToCaster(ChatColor.GOLD + ">> Потаємні думки: " + ChatColor.GRAY + "Пусто/Чисто");
            }
        }

        context.sendMessageToCaster(ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}