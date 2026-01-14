package me.vangoo.domain.pathways.visionary.abilities;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DangerSense extends ToggleablePassiveAbility {

    private static final int OBSERVE_TIME_TICKS = 20 * 20; // 20 секунд
    private static final double RANGE = 8.0;
    private static final long TARGET_COOLDOWN_MS = 150 * 1000; // 2.5 хвилини в мілісекундах

    // Сховище станів: CasterUUID -> (TargetUUID -> Ticks)
    private final Map<UUID, Map<UUID, Integer>> observationProgress = new ConcurrentHashMap<>();

    // Сховище кулдаунів: CasterUUID -> (TargetUUID -> ExpirationTimestamp)
    private final Map<UUID, Map<UUID, Long>> targetCooldowns = new ConcurrentHashMap<>();

    private static final Set<Material> DANGEROUS_ITEMS = EnumSet.of(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            Material.TNT, Material.TNT_MINECART,
            Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.LAVA_BUCKET, Material.FIRE_CHARGE, Material.FLINT_AND_STEEL
    );

    @Override
    public String getName() {
        return "Психічне відлуння";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дозволяє відчути небезпечні наміри. Якщо перебувати поруч із гравцем ("
                + RANGE + "б) протягом " + (OBSERVE_TIME_TICKS / 20) + "с, ви дізнаєтесь про зброю в його інвентарі.";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        observationProgress.put(casterId, new ConcurrentHashMap<>());
        targetCooldowns.putIfAbsent(casterId, new ConcurrentHashMap<>());

        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        observationProgress.remove(casterId);
        // Кулдауни зазвичай краще залишати до рестарту або очищувати окремо,
        // щоб не аб'юзити вимкненням/увімкненням.
        context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Map<UUID, Integer> myTargets = observationProgress.get(casterId);
        Map<UUID, Long> myCooldowns = targetCooldowns.get(casterId);

        if (myTargets == null) return;

        List<Player> nearbyPlayers = context.getNearbyPlayers(RANGE);
        long currentTime = System.currentTimeMillis();

        // 1. Очищуємо прогрес для тих, хто відійшов задалеко
        Set<UUID> nearbyIds = nearbyPlayers.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        myTargets.keySet().removeIf(id -> !nearbyIds.contains(id));

        // 2. Опрацьовуємо гравців поруч
        for (Player target : nearbyPlayers) {
            UUID targetId = target.getUniqueId();

            // Пропускаємо, якщо ціль на кулдауні
            if (myCooldowns != null && myCooldowns.getOrDefault(targetId, 0L) > currentTime) {
                continue;
            }

            int currentTicks = myTargets.getOrDefault(targetId, 0) + 1;

            if (currentTicks >= OBSERVE_TIME_TICKS) {
                // Час вийшов — виявляємо небезпеку
                revealDanger(context, target);

                // Ставимо кулдаун на цю ціль
                myCooldowns.put(targetId, currentTime + TARGET_COOLDOWN_MS);
                myTargets.remove(targetId);
            } else {
                myTargets.put(targetId, currentTicks);

                // Візуальний ефект підказки (раз на 2 секунди)
                if (currentTicks % 40 == 0) {
                    context.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f + (currentTicks / 400f));
                }
            }
        }
    }

    private void revealDanger(IAbilityContext context, Player target) {
        List<String> foundItems = new ArrayList<>();

        for (ItemStack item : target.getInventory().getContents()) {
            if (item != null && DANGEROUS_ITEMS.contains(item.getType())) {
                String itemName = item.getType().toString().replace("_", " ").toLowerCase();
                if (!foundItems.contains(itemName)) {
                    foundItems.add(itemName);
                }
            }
        }

        if (foundItems.isEmpty()) {
            context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.GRAY + "Ви відчули " + ChatColor.WHITE + target.getName() +
                            ChatColor.GRAY + ", але не знайшли нічого підозрілого."));
        } else {
            context.sendMessageToActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.RED + "⚠️ Передчуття! У " + ChatColor.YELLOW + target.getName() +
                            ChatColor.RED + " знайдено: " + ChatColor.GOLD + String.join(", ", foundItems)));
            context.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

            // Підсвічуємо небезпечного гравця на 5 секунд
            context.setGlowing(target.getUniqueId(), ChatColor.RED, 100);
        }
    }

    @Override
    public void cleanUp() {
        observationProgress.clear();
        targetCooldowns.clear();
    }
}