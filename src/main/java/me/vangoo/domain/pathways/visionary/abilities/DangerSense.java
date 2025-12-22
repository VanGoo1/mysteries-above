package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DangerSense extends ToggleablePassiveAbility {
    private static final int OBSERVE_TIME_TICKS = 20 * 20; // 20 секунд
    private static final int RANGE = 8; // 8 блоків
    private static final int COOLDOWN_PER_TARGET = 150; // 2.5 хвилини в секундах
    private static final int CHECK_INTERVAL = 20; // перевірка кожну секунду

    // Track which casters have this ability enabled
    private final Set<UUID> enabledCasters = ConcurrentHashMap.newKeySet();

    // Поточні цілі спостереження {caster -> {target -> ticks}}
    private final Map<UUID, Map<UUID, Integer>> currentTargets = new ConcurrentHashMap<>();

    // Кулдауни на конкретних цілей {caster -> {target -> expireTime}}
    private final Map<UUID, Map<UUID, Long>> targetCooldowns = new ConcurrentHashMap<>();

    @Override
    public void cleanUp(){
        enabledCasters.clear();
        currentTargets.clear();
        targetCooldowns.clear();
    }

    // Небезпечні предмети
    private static final Set<Material> DANGEROUS_ITEMS = Set.of(
            // Мечі
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,

            // Луки та арбалети
            Material.BOW, Material.CROSSBOW,

            // Вибухівка
            Material.TNT, Material.TNT_MINECART,

            // Зілля
            Material.SPLASH_POTION, Material.LINGERING_POTION,

            // Тризуби
            Material.TRIDENT,

            // Сокири (можуть бути зброєю)
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,

            // Інші небезпечні предмети
            Material.LAVA_BUCKET, Material.FIRE_CHARGE, Material.FLINT_AND_STEEL
    );


    @Override
    public String getName() {
        return "[Пасивна] Психічне відлуння";
    }

    @Override
    public String getDescription() {
        return "Якщо стояти поруч з гравцем впродовж" + OBSERVE_TIME_TICKS / 20 + "с на відстані до " + RANGE + " блоків, то можна виявити небезпечні предмети в його інвентарі.";
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        if (enabledCasters.contains(casterId)) {
            // Вимкнути здібність
            enabledCasters.remove(casterId);
            currentTargets.remove(casterId);
            context.sendMessageToCaster(ChatColor.YELLOW + "Відчуття небезпеки" + ChatColor.GRAY + " вимкнене.");
        } else {
            // Увімкнути здібність
            enabledCasters.add(casterId);
            currentTargets.put(casterId, new ConcurrentHashMap<>());
            if (!targetCooldowns.containsKey(casterId)) {
                targetCooldowns.put(casterId, new ConcurrentHashMap<>());
            }
            context.sendMessageToCaster(ChatColor.GREEN + "Відчуття небезпеки" + ChatColor.GRAY + " увімкнене. Підійдіть до гравця на " + RANGE + " блоків.");
        }

        return AbilityResult.success();
    }

    @Override
    public void onEnable(IAbilityContext context) {

    }

    @Override
    public void onDisable(IAbilityContext context) {

    }

    @Override
    public void tick(IAbilityContext context) {

    }
}