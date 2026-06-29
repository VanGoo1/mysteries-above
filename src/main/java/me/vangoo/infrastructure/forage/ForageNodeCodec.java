package me.vangoo.infrastructure.forage;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** PDC-тег ноди фореджу на сутності (патерн CreatureCodec). Тегуються обидві сутності ноди. */
public class ForageNodeCodec {

    private final NamespacedKey nodeKey;
    private final NamespacedKey ingredientKey;
    private final NamespacedKey partnerKey;

    public ForageNodeCodec(Plugin plugin) {
        this.nodeKey = new NamespacedKey(plugin, "forage_node");
        this.ingredientKey = new NamespacedKey(plugin, "forage_ingredient");
        this.partnerKey = new NamespacedKey(plugin, "forage_partner");
    }

    public void tag(Entity e, String ingredientId, UUID partner) {
        e.getPersistentDataContainer().set(nodeKey, PersistentDataType.BYTE, (byte) 1);
        e.getPersistentDataContainer().set(ingredientKey, PersistentDataType.STRING, ingredientId);
        e.getPersistentDataContainer().set(partnerKey, PersistentDataType.STRING, partner.toString());
    }

    public boolean isForageNode(Entity e) {
        return e != null && e.getPersistentDataContainer().has(nodeKey, PersistentDataType.BYTE);
    }

    public Optional<String> readIngredient(Entity e) {
        if (e == null) return Optional.empty();
        return Optional.ofNullable(e.getPersistentDataContainer().get(ingredientKey, PersistentDataType.STRING));
    }

    public Optional<UUID> readPartner(Entity e) {
        if (e == null) return Optional.empty();
        String s = e.getPersistentDataContainer().get(partnerKey, PersistentDataType.STRING);
        if (s == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(s)); } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
