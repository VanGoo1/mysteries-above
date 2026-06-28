package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureStats;
import me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Спавнить кастомну істоту: ванільний базовий ентіті + стати + вигляд + PDC-тег. */
public final class CreatureSpawner {

    private final Map<String, CreatureAppearance> appearances;
    private final CreatureCodec codec;
    private final Plugin plugin;
    private final CreatureBehaviorManager behaviorManager;

    public CreatureSpawner(Map<String, CreatureAppearance> appearances, CreatureCodec codec, Plugin plugin,
                           CreatureBehaviorManager behaviorManager) {
        this.appearances = appearances;
        this.codec = codec;
        this.plugin = plugin;
        this.behaviorManager = behaviorManager;
    }

    public Optional<LivingEntity> spawn(CreatureDefinition def, Location loc) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();

        EntityType type;
        try {
            type = EntityType.valueOf(def.baseEntityType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Creature '" + def.id() + "': unknown base_entity '"
                    + def.baseEntityType() + "'; skipping spawn");
            return Optional.empty();
        }

        Entity spawned = loc.getWorld().spawnEntity(loc, type);
        if (!(spawned instanceof LivingEntity living)) {
            plugin.getLogger().warning("Creature '" + def.id() + "': base_entity '"
                    + def.baseEntityType() + "' is not a LivingEntity; removing");
            spawned.remove();
            return Optional.empty();
        }

        applyStats(living, def.stats());

        CreatureAppearance appearance = appearances.getOrDefault(def.appearance(), appearances.get("vanilla"));
        if (appearance != null) {
            appearance.apply(living, def);
        }
        codec.tag(living, def.id());
        behaviorManager.start(living, def);
        return Optional.of(living);
    }

    private void applyStats(LivingEntity e, CreatureStats s) {
        setAttr(e, Attribute.MAX_HEALTH, s.health());
        AttributeInstance maxHp = e.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            e.setHealth(Math.min(s.health(), maxHp.getValue()));
        }
        setAttr(e, Attribute.ATTACK_DAMAGE, s.damage());
        setAttr(e, Attribute.MOVEMENT_SPEED, s.speed());
    }

    private void setAttr(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }
}
