package me.vangoo.infrastructure.creatures;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/** Тегує сутність id кастомної істоти у її PersistentDataContainer. */
public final class CreatureCodec {

    private final NamespacedKey idKey;

    public CreatureCodec(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "creature_id");
    }

    public void tag(Entity entity, String creatureId) {
        if (entity == null || creatureId == null) return;
        entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, creatureId);
    }

    public boolean isCreature(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public Optional<String> readId(Entity entity) {
        if (entity == null) return Optional.empty();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(idKey, PersistentDataType.STRING));
    }
}
