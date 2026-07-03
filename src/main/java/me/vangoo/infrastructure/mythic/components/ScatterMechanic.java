package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

@MythicMechanic(author = "mysteries-above", name = "scatter",
        description = "Teleports the target a few blocks to a random safe spot; Weakness fallback")
public class ScatterMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final double radius;

    public ScatterMechanic(SkillExecutor manager, File file, String line, MythicLineConfig mlc) {
        super(manager, file, line, mlc);
        this.radius = mlc.getDouble(new String[]{"radius", "r"}, 5.0);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity bukkit = target.getBukkitEntity();
        double dx = ThreadLocalRandom.current().nextDouble(-radius, radius);
        double dz = ThreadLocalRandom.current().nextDouble(-radius, radius);
        Location dest = SafeLocations.passableNear(bukkit.getLocation().clone().add(dx, 0, dz));
        var below = dest.clone().subtract(0, 1, 0).getBlock();
        boolean lava = dest.getBlock().getType() == Material.LAVA || below.getType() == Material.LAVA;
        if (below.getType().isSolid() && !lava) {
            bukkit.teleport(dest);
        } else if (bukkit instanceof org.bukkit.entity.LivingEntity living) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false));
        }
        return SkillResult.SUCCESS;
    }
}
