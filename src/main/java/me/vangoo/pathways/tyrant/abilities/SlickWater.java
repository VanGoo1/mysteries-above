package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Sequence 5: Слизька Вода. Океанський Співець розливає липку воду, що знищує тертя:
 * хто ступив у пляму — з'їжджає за власним рухом і не може відштовхнутись для стрибка.
 */
public class SlickWater extends ActiveAbility {

    private static final double RADIUS = 4.0;
    private static final double MAX_RANGE = 20.0;
    private static final double SLIDE_GAIN = 0.45;   // наскільки підсилюється власний рух цілі
    private static final double SLIDE_CAP = 1.1;     // щоб не жбурляло через пів карти
    private static final int DURATION_TICKS = 200;   // 10 с
    private static final int COOLDOWN = 25;
    private static final int SPIRITUALITY_COST = 60;

    @Override
    public String getName() {
        return "Слизька Вода";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return String.format(
                "§fРозливає липку воду без тертя в радіусі §b%.0f §fблоків на §b10 с§f: " +
                        "усе живе там §bз'їжджає за власним рухом§f і §bне може стрибнути§f.",
                RADIUS);
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

        Block aimed = player.getTargetBlockExact((int) MAX_RANGE);
        if (aimed == null) {
            return AbilityResult.failure("Немає поверхні, щоб розлити воду");
        }
        Location center = aimed.getLocation().add(0.5, 1.0, 0.5);

        context.effects().playCircleEffect(center, RADIUS, Particle.SPLASH, DURATION_TICKS);
        context.effects().playWaveEffect(center, RADIUS, Particle.DRIPPING_WATER, 12);
        context.effects().playSound(center, Sound.ITEM_BUCKET_EMPTY, 1.2f, 0.6f);
        context.effects().playSound(center, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 0.7f);

        int[] tick = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            tick[0]++;
            if (tick[0] > DURATION_TICKS || center.getWorld() == null) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }

            if (tick[0] % 5 == 0) {
                center.getWorld().spawnParticle(Particle.DRIPPING_WATER, center, 12,
                        RADIUS / 2, 0.2, RADIUS / 2);
            }

            for (Entity entity : center.getWorld().getNearbyEntities(center, RADIUS, 2.0, RADIUS)) {
                if (!(entity instanceof LivingEntity target)) continue;

                // Ноги не знаходять опори: стрибок неможливий (128 — ідіома «нуль стрибка»).
                context.entity().applyPotionEffect(target.getUniqueId(),
                        PotionEffectType.JUMP_BOOST, 10, 128);

                // Тертя нема: власний горизонтальний рух цілі підсилюється й несе її далі.
                Vector velocity = target.getVelocity();
                Vector horizontal = new Vector(velocity.getX(), 0, velocity.getZ());
                if (horizontal.lengthSquared() < 1.0e-4) continue;
                Vector slide = horizontal.multiply(SLIDE_GAIN);
                if (slide.length() > SLIDE_CAP) slide = slide.normalize().multiply(SLIDE_CAP);
                target.setVelocity(velocity.add(slide));
            }
        }, 1L, 1L);

        return AbilityResult.success();
    }
}
