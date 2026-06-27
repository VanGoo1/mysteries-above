package me.vangoo.infrastructure.creatures;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.creatures.CreatureDefinition;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

/** Ванільний вигляд: кастомне ім'я, розмір (SCALE), опційний екіп. */
public final class VanillaAppearance implements CreatureAppearance {

    private final CustomItemService customItemService;

    public VanillaAppearance(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    @Override
    public void apply(LivingEntity entity, CreatureDefinition def) {
        entity.setCustomName(def.displayName());
        entity.setCustomNameVisible(true);

        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(def.stats().scale());
        }

        EntityEquipment eq = entity.getEquipment();
        if (eq != null && !def.equipment().isEmpty()) {
            for (Map.Entry<String, String> e : def.equipment().entrySet()) {
                EquipmentSlot slot = parseSlot(e.getKey());
                if (slot == null || e.getValue() == null) continue;
                ItemStack stack = customItemService.createItemStack(e.getValue()).orElse(null);
                if (stack == null) continue;
                eq.setItem(slot, stack);
                setDropChance(eq, slot, 0f); // екіп не падає окремо — дроп лише з лут-таблиці
            }
        }
    }

    private EquipmentSlot parseSlot(String key) {
        try {
            return EquipmentSlot.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Встановлює шанс дропу для конкретного слота (1.21.1 API не має загального setDropChance(EquipmentSlot, float)). */
    private static void setDropChance(EntityEquipment eq, EquipmentSlot slot, float chance) {
        switch (slot) {
            case HAND      -> eq.setItemInMainHandDropChance(chance);
            case OFF_HAND  -> eq.setItemInOffHandDropChance(chance);
            case HEAD      -> eq.setHelmetDropChance(chance);
            case CHEST     -> eq.setChestplateDropChance(chance);
            case LEGS      -> eq.setLeggingsDropChance(chance);
            case FEET      -> eq.setBootsDropChance(chance);
            default        -> { /* BODY/інші слоти — ігноруємо */ }
        }
    }
}
