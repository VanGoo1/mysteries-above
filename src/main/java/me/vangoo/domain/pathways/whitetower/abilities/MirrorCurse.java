package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Optional;

public class MirrorCurse extends ActiveAbility {

    private static final double RANGE = 15.0;
    private static final double BASE_DAMAGE = 4.0;
    private static final double MISSING_HEALTH_FACTOR = 0.35;

    @Override
    public String getName() {
        return "Дзеркальне Прокляття";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Використовує латунне дзеркало, щоб відобразити рани ворога...\n" + // скорочено для зручності
                "§c▪ Умова: §fЦіль повинна бути поранена";
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
        Player caster = context.getCasterPlayer();
        World world = caster.getWorld();

        // 1. Стандартний Bukkit RayTrace
        // Шукаємо сутності, ігноруємо блоки, ігноруємо самого кастера
        RayTraceResult result = world.rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                RANGE,
                0.5, // Розмір променя (hitbox)
                entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(caster.getUniqueId())
        );

        if (result == null || result.getHitEntity() == null) {
            return AbilityResult.failure("✘ Немає цілі для відображення");
        }

        LivingEntity target = (LivingEntity) result.getHitEntity();

        // 2. Перевірка здоров'я
        double maxHealth = 20.0;
        var maxHealthAttr = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) maxHealth = maxHealthAttr.getValue();

        double currentHealth = target.getHealth();
        double missingHealth = maxHealth - currentHealth;

        if (missingHealth <= 0) {
            context.effects().playSoundForPlayer(caster.getUniqueId(), Sound.BLOCK_GLASS_HIT, 1.0f, 0.5f);
            return AbilityResult.failure("✘ Ціль неушкоджена. Нічого погіршувати.");
        }

        // 3. Логіка
        double extraDamage = missingHealth * MISSING_HEALTH_FACTOR;
        double totalDamage = BASE_DAMAGE + extraDamage;

        target.damage(totalDamage, caster);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));

        // 4. Візуалізація
        playMirrorEffect(context, caster, target);

        context.messaging().sendMessageToActionBar(caster.getUniqueId(),
                Component.text("☠ Рана поглибилась на ", NamedTextColor.DARK_RED)
                        .append(Component.text(String.format("%.1f", totalDamage), NamedTextColor.RED))
                        .append(Component.text(" HP", NamedTextColor.RED)));

        return AbilityResult.success();
    }

    private void playMirrorEffect(IAbilityContext context, Player caster, LivingEntity target) {
        Location casterEye = caster.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        World world = caster.getWorld(); // Потрібен для spawnParticle з DustOptions

        Vector direction = casterEye.getDirection().normalize();
        Location mirrorLoc = casterEye.clone().add(direction.multiply(1.2));

        // Звуки (через контекст, як і було)
        context.effects().playSound(mirrorLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        context.effects().playSound(targetEye, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
        context.effects().playSoundForPlayer(caster.getUniqueId(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 2.0f);

        // Колір латуні
        Particle.DustOptions brassOptions = new Particle.DustOptions(Color.fromRGB(181, 166, 66), 1.0f);
        // Колір крові
        Particle.DustOptions bloodOptions = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.0f);

        // 2. Рамка дзеркала
        drawMirrorFrame(world, mirrorLoc, direction, brassOptions);

        // 3. Нитка крові (від цілі до дзеркала)
        double distance = mirrorLoc.distance(targetEye);
        Vector vectorToMirror = mirrorLoc.toVector().subtract(targetEye.toVector()).normalize();

        for (double d = 0; d < distance; d += 0.5) {
            Location point = targetEye.clone().add(vectorToMirror.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, bloodOptions);
        }

        // 4. Ефект розбиття (через планувальник)
        context.scheduling().scheduleDelayed(() -> {
            // Для скла використовуємо BlockData
            world.spawnParticle(Particle.BLOCK, mirrorLoc, 30, 0.3, 0.3, 0.3, Material.GLASS.createBlockData());

            // Звичайні кріти через ваш контекст
            context.effects().spawnParticle(Particle.CRIT, mirrorLoc, 15, 0.2, 0.2, 0.2);

            // Латунні іскри (знову через native world, бо треба колір)
            world.spawnParticle(Particle.DUST, mirrorLoc, 20, 0.4, 0.4, 0.4, brassOptions);
        }, 5L);
    }

    private void drawMirrorFrame(World world, Location center, Vector lookDir, Particle.DustOptions options) {
        Vector right = new Vector(-lookDir.getZ(), 0, lookDir.getX()).normalize().multiply(0.4);
        Vector up = new Vector(0, 1, 0).multiply(0.6);

        Location[] corners = new Location[]{
                center.clone().add(right.clone().multiply(-1)).add(up),       // Top Left
                center.clone().add(right).add(up),                            // Top Right
                center.clone().add(right.clone().multiply(-1)).subtract(up),  // Bot Left
                center.clone().add(right).subtract(up)                        // Bot Right
        };

        for (Location loc : corners) {
            world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, options);
        }

        // Центр (звичайний ефект, можна через context)
        world.spawnParticle(Particle.END_ROD, center, 3, 0.2, 0.3, 0.2, 0.01);
    }
}