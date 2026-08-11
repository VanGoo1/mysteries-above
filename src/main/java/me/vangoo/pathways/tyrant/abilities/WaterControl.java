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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 6: Владарювання Водою. Благословенний Вітром керує вже наявною водою — тож
 * здібність працює лише коли поруч є вода або йде дощ. Одна здібність із трьома
 * виходами (Shift+каст перемикає): струмінь, щит і просочення.
 */
public class WaterControl extends ActiveAbility {

    private static final int BASE_JET_DAMAGE = 7;
    private static final int BASE_SOAK_DAMAGE = 3;
    private static final double MAX_RANGE = 20.0;
    private static final double JET_KNOCKBACK = 1.6;
    private static final int SHIELD_TICKS = 120;   // ~6 с
    private static final int SOAK_TICKS = 100;     // ~5 с
    private static final int WATER_SCAN_RADIUS = 4;
    private static final int COOLDOWN = 8;
    private static final int SPIRITUALITY_COST = 60;

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    private enum Mode {
        JET("Струмінь"), SHIELD("Щит"), SOAK("Просочення");

        final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    @Override
    public String getName() {
        return "Владарювання Водою";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int jet = scaleValue(BASE_JET_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int soak = scaleValue(BASE_SOAK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fКерує наявною водою — §lлише біля води або під дощем§r§f.\n" +
                        "§bСтрумінь§f: §c%d §fшкоди й відкид цілі.\n§bЩит§f: водяна завіса, " +
                        "опір і регенерація на 6 с.\n§bПросочення§f: ціль набрякає водою — " +
                        "§c%d §fшкоди, сповільнення й збите полум'я.\n§7Shift+каст — перемкнути режим.",
                jet, soak);
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
        UUID casterId = context.getCasterId();

        // Перемикання режиму — без ціни й кулдауну.
        if (context.playerData().isSneaking(casterId)) {
            Mode next = modes.getOrDefault(casterId, Mode.JET).next();
            modes.put(casterId, next);
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("≈ Режим води: " + next.displayName, NamedTextColor.AQUA));
            context.effects().playSoundForPlayer(casterId, Sound.ITEM_BUCKET_FILL, 0.8f, 1.4f);
            return AbilityResult.deferred();
        }

        // Гейт ДО будь-якого ефекту: без води довкола керувати нічим.
        if (!hasWaterSource(player)) {
            return AbilityResult.failure("Поруч немає води — і дощу теж");
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        return switch (modes.getOrDefault(casterId, Mode.JET)) {
            case JET -> castJet(context, player, sequence);
            case SHIELD -> castShield(context, player);
            case SOAK -> castSoak(context, player, sequence);
        };
    }

    /** Вода поруч: гравець у воді, водний блок у радіусі, або дощ під відкритим небом. */
    private boolean hasWaterSource(Player player) {
        if (player.isInWater()) return true;

        World world = player.getWorld();
        Location loc = player.getLocation();
        if (world.hasStorm() && world.getHighestBlockYAt(loc) <= loc.getBlockY()) return true;

        for (int x = -WATER_SCAN_RADIUS; x <= WATER_SCAN_RADIUS; x++) {
            for (int y = -WATER_SCAN_RADIUS; y <= WATER_SCAN_RADIUS; y++) {
                for (int z = -WATER_SCAN_RADIUS; z <= WATER_SCAN_RADIUS; z++) {
                    if (loc.clone().add(x, y, z).getBlock().getType() == Material.WATER) return true;
                }
            }
        }
        return false;
    }

    private AbilityResult castJet(IAbilityContext context, Player player, Sequence sequence) {
        Optional<LivingEntity> targeted = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targeted.isEmpty()) {
            return AbilityResult.failure("Немає цілі на лінії зору");
        }
        LivingEntity target = targeted.get();
        int damage = scaleValue(BASE_JET_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        var color = PathwayBranding.liquidOf("Tyrant");

        context.effects().playTravelingBeam(player.getEyeLocation(), target.getEyeLocation(), color, () -> {
            target.damage(damage, player);
            target.setFireTicks(0);
            Vector push = target.getLocation().toVector()
                    .subtract(player.getLocation().toVector()).setY(0);
            if (push.lengthSquared() < 1.0e-4) push = player.getLocation().getDirection().setY(0);
            target.setVelocity(push.normalize().multiply(JET_KNOCKBACK).setY(0.35));
            context.effects().playWaveEffect(target.getLocation(), 2.0, Particle.SPLASH, 6);
            context.effects().playSound(target.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.9f);
        });
        context.effects().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.2f);
        return AbilityResult.success();
    }

    private AbilityResult castShield(IAbilityContext context, Player player) {
        UUID casterId = player.getUniqueId();
        var color = PathwayBranding.liquidOf("Tyrant");

        context.entity().applyPotionEffect(casterId, PotionEffectType.RESISTANCE, SHIELD_TICKS, 1);
        context.entity().applyPotionEffect(casterId, PotionEffectType.REGENERATION, SHIELD_TICKS, 0);
        player.setFireTicks(0);

        context.effects().playRisingSpiral(player.getLocation(), 2.2, 1.0, color, SHIELD_TICKS);
        context.effects().playCircleEffect(player.getLocation().add(0, 1.0, 0), 1.2,
                Particle.DRIPPING_WATER, SHIELD_TICKS);
        context.effects().playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 1.0f, 0.9f);
        return AbilityResult.success();
    }

    private AbilityResult castSoak(IAbilityContext context, Player player, Sequence sequence) {
        Optional<LivingEntity> targeted = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targeted.isEmpty()) {
            return AbilityResult.failure("Немає цілі на лінії зору");
        }
        LivingEntity target = targeted.get();
        int damage = scaleValue(BASE_SOAK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        target.setFireTicks(0);
        target.damage(damage, player);
        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, SOAK_TICKS, 1);
        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.MINING_FATIGUE, SOAK_TICKS, 1);

        context.effects().playSphereEffect(target.getLocation().add(0, 1.0, 0), 1.4,
                Particle.DRIPPING_WATER, SOAK_TICKS);
        context.effects().playWaveEffect(target.getLocation(), 1.6, Particle.SPLASH, 8);
        context.effects().playSound(target.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.8f);
        return AbilityResult.success();
    }
}
