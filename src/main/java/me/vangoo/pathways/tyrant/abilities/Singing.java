package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 5: Спів. Океанський Співець впливає голосом — і це «обмін» на духовному рівні,
 * тож стіни, відстань і затулені вуха не рятують: перевірки лінії зору немає. Одна
 * здібність із трьома голосами (Shift+каст перемикає): чарівний спів, какофонія, громовий рев.
 */
public class Singing extends ActiveAbility {

    private static final double RADIUS = 12.0;
    private static final int EFFECT_TICKS = 180;       // ~9 с
    private static final double CHARM_AMPLIFY = 1.3;
    private static final int CHARM_AMPLIFY_SECONDS = 10;
    private static final double ROAR_KNOCKBACK = 1.3;
    private static final int COOLDOWN = 40;
    private static final int SPIRITUALITY_COST = 160;

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    private enum Mode {
        CHARM("Чарівний спів"), CACOPHONY("Какофонія"), ROAR("Громовий Рев");

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
        return "Спів";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return String.format(
                "§fГолос б'є духовно — §lкрізь стіни й затулені вуха§r§f, у радіусі §b%d §fблоків.\n" +
                        "§bЧарівний спів§f: вороги марніють і втрачають орієнтацію, а ваша шкода " +
                        "зростає на §c30%%§f.\n§bКакофонія§f: мобів охоплює лють — вони кидаються " +
                        "один на одного, гравці слабнуть.\n§bГромовий Рев§f: усіх відкидає, " +
                        "моби тікають, гравців накриває пітьма.\n§7Shift+каст — перемкнути голос.",
                (int) RADIUS);
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

        // Перемикання голосу — без ціни й кулдауну.
        if (context.playerData().isSneaking(casterId)) {
            Mode next = modes.getOrDefault(casterId, Mode.CHARM).next();
            modes.put(casterId, next);
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("♪ Голос: " + next.displayName, NamedTextColor.AQUA));
            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            return AbilityResult.deferred();
        }

        Mode mode = modes.getOrDefault(casterId, Mode.CHARM);
        List<LivingEntity> listeners = listenersAround(player);

        switch (mode) {
            case CHARM -> castCharm(context, player, listeners);
            case CACOPHONY -> castCacophony(context, player, listeners);
            case ROAR -> castRoar(context, player, listeners);
        }
        return AbilityResult.success();
    }

    /** Усе живе в радіусі, окрім кастера. Лінію зору НЕ перевіряємо — спів пробиває стіни. */
    private List<LivingEntity> listenersAround(Player player) {
        Location center = player.getLocation();
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;
            if (living.getLocation().distanceSquared(center) > RADIUS * RADIUS) continue;
            found.add(living);
        }
        return found;
    }

    private void castCharm(IAbilityContext context, Player player, List<LivingEntity> listeners) {
        for (LivingEntity target : listeners) {
            context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.NAUSEA, EFFECT_TICKS, 0);
            context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, EFFECT_TICKS, 1);
        }
        context.amplification().amplifyDamage(player.getUniqueId(), CHARM_AMPLIFY, CHARM_AMPLIFY_SECONDS);

        var color = PathwayBranding.liquidOf("Tyrant");
        context.effects().playRisingSpiral(player.getLocation(), 3.0, 1.4, color, 40);
        context.effects().playWaveEffect(player.getLocation(), RADIUS, Particle.NOTE, 40);
        context.effects().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 0.8f);
        context.effects().playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS, 1.2f, 1.2f);
    }

    private void castCacophony(IAbilityContext context, Player player, List<LivingEntity> listeners) {
        List<Mob> mobs = listeners.stream().filter(Mob.class::isInstance).map(Mob.class::cast).toList();
        for (LivingEntity target : listeners) {
            if (target instanceof Player) {
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.NAUSEA, EFFECT_TICKS, 1);
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.WEAKNESS, EFFECT_TICKS, 1);
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.MINING_FATIGUE, EFFECT_TICKS, 1);
            }
        }
        // Втрата розсудливості: кожен моб кидається на сусіда, а не на співця.
        for (int i = 0; i < mobs.size(); i++) {
            Mob mob = mobs.get(i);
            if (mobs.size() > 1) {
                mob.setTarget(mobs.get((i + 1) % mobs.size()));
            }
        }

        var color = PathwayBranding.liquidOf("Tyrant");
        context.effects().playVortexEffect(player.getLocation().add(0, 1.0, 0), 3.0, 1.6, Particle.ANGRY_VILLAGER, 40);
        context.effects().playFadingAura(player.getLocation(), color, 40);
        context.effects().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.6f, 0.5f);
        context.effects().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.6f);
    }

    private void castRoar(IAbilityContext context, Player player, List<LivingEntity> listeners) {
        Location center = player.getLocation();
        for (LivingEntity target : listeners) {
            if (target instanceof Player) {
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, EFFECT_TICKS, 1);
                context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.DARKNESS, EFFECT_TICKS, 0);
            } else if (target instanceof Mob mob) {
                mob.setTarget(null); // благоговійний жах — моб перестає бачити ціль
            }
            Vector push = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() < 1.0e-4) push = center.getDirection().setY(0);
            target.setVelocity(push.normalize().multiply(ROAR_KNOCKBACK).setY(0.35));
        }

        var color = PathwayBranding.liquidOf("Tyrant");
        context.effects().playExplosionRingEffect(center, RADIUS, Particle.DUST,
                new Particle.DustOptions(color, 1.6f));
        context.effects().playWaveEffect(center, RADIUS, Particle.SONIC_BOOM, 30);
        context.effects().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.8f, 0.7f);
        context.effects().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.0f);
    }
}
