package me.vangoo.domain.pathways.door.abilities;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

public class Burning extends ActiveAbility {

    private static final double RANGE = 5.0;
    private static final int COST = 20;
    private static final int COOLDOWN = 3;
    @Override
    public String getName() {
        return "Підпал";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Я люблю як воно палає, як горить...";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        World world = caster.getWorld();

        RayTraceResult result = world.rayTrace(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                RANGE,
                FluidCollisionMode.NEVER,
                true,
                0.3,
                entity -> entity != caster
        );

        if (result == null) {
            return AbilityResult.failure("Немає цілі");
        }

        if (result.getHitEntity() instanceof LivingEntity living) {

            living.setFireTicks(60); // 3 секунди

            world.spawnParticle(
                    Particle.DUST,
                    living.getLocation().add(0, 1, 0),
                    20,
                    0.3, 0.4, 0.3,
                    new Particle.DustOptions(Color.RED, 1.2f)
            );

            world.playSound(
                    living.getLocation(),
                    Sound.ITEM_FLINTANDSTEEL_USE,
                    1.0f,
                    1.2f
            );

            return AbilityResult.success();
        }

        Block block = result.getHitBlock();
        if (block != null) {

            Block fireBlock = block.getRelative(result.getHitBlockFace());

            if (fireBlock.getType() == Material.AIR) {
                fireBlock.setType(Material.FIRE);
            }

            world.spawnParticle(
                    Particle.DUST,
                    fireBlock.getLocation().add(0.5, 0.5, 0.5),
                    15,
                    0.2, 0.2, 0.2,
                    new Particle.DustOptions(Color.RED, 1.0f)
            );

            world.playSound(
                    fireBlock.getLocation(),
                    Sound.ITEM_FLINTANDSTEEL_USE,
                    1.0f,
                    1.0f
            );

            return AbilityResult.success();
        }

        return AbilityResult.failure("Неможливо підпалити");
    }
}
