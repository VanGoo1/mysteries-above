package me.vangoo.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WhipOfPain extends ActiveAbility {

    private static final double RANGE = 10.0;
    private static final double DAMAGE = 7.0;

    /** Глибина провисання батога посередині — саме вона робить лінію «хлистом», а не променем. */
    private static final double SAG_DEPTH = 0.45;
    private static final Particle.DustOptions WHIP_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 240, 120), 0.8f);

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

        // Позиції ЦІЛІ беремо з самої сутності, а не через playerData(): той резолвить
        // UUID через Bukkit.getPlayer() і на будь-якому мобі повертає null — через це весь
        // блок ефектів мовчки пропускався, і батіг бив «невидимо».
        Location casterEye = context.playerData().getEyeLocation(casterId);
        Location targetEye = target.getEyeLocation();

        if (casterEye != null) {
            drawWhip(casterEye.clone().add(0, -0.2, 0), targetEye);
        }
        context.effects().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);

        context.entity().damage(targetId, DAMAGE);
        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, 60, 4);
        context.entity().applyPotionEffect(targetId, PotionEffectType.BLINDNESS, 40, 0);

        revealDeepSecrets(context, casterId, target);

        if (context.playerData().isOnline(targetId)) {
            context.messaging().sendMessage(targetId, ChatColor.RED + "Ви не можете стримати правду... Біль змушує вас говорити!");
        }

        return AbilityResult.success();
    }

    /**
     * Малює батіг: провисла дуга електричних іскр від руки кастера до цілі плюс удар на цілі.
     *
     * <p>Прямий Bukkit — партикли з {@code DustOptions}, яких {@code IVisualEffectsContext}
     * не вміє. Раніше тут був {@code playBeamEffect} з {@code Particle.CRIT}, але до нього
     * не доходило взагалі: {@code targetEye} для моба був null.
     */
    private void drawWhip(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) return;

        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        if (length < 0.1) return;

        int points = Math.max(12, (int) (length * 6));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            double sag = Math.sin(t * Math.PI) * SAG_DEPTH; // найглибше — посередині
            Location point = from.clone().add(span.clone().multiply(t)).subtract(0, sag, 0);

            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, WHIP_DUST);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0);
            }
        }

        world.spawnParticle(Particle.ELECTRIC_SPARK, to, 25, 0.35, 0.35, 0.35, 0.15);
        world.spawnParticle(Particle.CRIT, to, 12, 0.3, 0.3, 0.3, 0.2);
    }

    private void revealDeepSecrets(IAbilityContext context, UUID casterId, LivingEntity target) {
        UUID targetId = target.getUniqueId();
        context.messaging().sendMessage(casterId, ChatColor.DARK_AQUA + "▬▬▬▬ ПРИМУСОВИЙ ДОПИТ ▬▬▬▬");

        // Ім'я та HP — теж із сутності: playerData() віддав би "" і 0 для моба.
        String targetName = target.getName();
        double hp = target.getHealth();

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