package me.vangoo.infrastructure.items;

import me.vangoo.domain.brewing.Characteristic;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Зберігає «есенцію всередині» сутності (Бешаного Warden) у її {@link PersistentDataContainer}:
 * шлях+послідовність загиблого Beyonder. Виплата відбувається при смерті сутності
 * ({@code RampageRemnantDeathListener}).
 */
public final class WardenRemnantCodec {

    private final NamespacedKey pathwayKey;
    private final NamespacedKey sequenceKey;

    public WardenRemnantCodec(Plugin plugin) {
        this.pathwayKey  = new NamespacedKey(plugin, "characteristic_remnant_pathway");
        this.sequenceKey = new NamespacedKey(plugin, "characteristic_remnant_sequence");
    }

    /** Тегує сутність шляхом+послідовністю есенції, яку вона несе. */
    public void tag(Entity entity, String pathwayName, int sequence) {
        if (entity == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(pathwayKey, PersistentDataType.STRING, pathwayName);
        pdc.set(sequenceKey, PersistentDataType.INTEGER, sequence);
    }

    /** Чи несе сутність есенцію Характеристики. */
    public boolean isRemnant(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(pathwayKey, PersistentDataType.STRING);
    }

    /** Читає (шлях, seq) есенції, якщо вона є. */
    public Optional<Characteristic> read(Entity entity) {
        if (!isRemnant(entity)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String pathway = pdc.get(pathwayKey, PersistentDataType.STRING);
        Integer sequence = pdc.get(sequenceKey, PersistentDataType.INTEGER);
        if (pathway == null || sequence == null) {
            return Optional.empty();
        }
        return Optional.of(new Characteristic(pathway, sequence));
    }
}
