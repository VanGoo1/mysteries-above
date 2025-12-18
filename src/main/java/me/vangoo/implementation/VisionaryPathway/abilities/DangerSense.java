package me.vangoo.implementation.VisionaryPathway.abilities;

import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getLogger;

public class DangerSense extends Ability {
    private static final int OBSERVE_TIME_TICKS = 20 * 20; // 20 секунд
    private static final int RANGE = 8; // 8 блоків
    private static final int COOLDOWN_PER_TARGET = 150; // 2.5 хвилини в секундах
    private static final int CHECK_INTERVAL = 20; // перевірка кожну секунду

    // Активні завдання спостереження для кожного гравця
    private final Map<UUID, BukkitTask> observeTasks = new ConcurrentHashMap<>();

    // Поточні цілі спостереження {caster -> {target -> ticks}}
    private final Map<UUID, Map<UUID, Integer>> currentTargets = new ConcurrentHashMap<>();

    // Кулдауни на конкретних цілей {caster -> {target -> expireTime}}
    private final Map<UUID, Map<UUID, Long>> targetCooldowns = new ConcurrentHashMap<>();

    @Override
    public void cleanUp(){
        for (BukkitTask task : observeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                try {
                    task.cancel();
                } catch (Exception e) {
                    getLogger().warning("error with canceling task");
                }
            }
        }

        observeTasks.clear();
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

    private static class TargetInfo {
        UUID targetId;
        int ticks;

        TargetInfo(UUID targetId) {
            this.targetId = targetId;
            this.ticks = 0;
        }
    }

    @Override
    public String getName() {
        return "[Пасивна] Психічне відлуння";
    }

    @Override
    public String getDescription() {
        return "Якщо стояти поруч з гравцем впродовж" + OBSERVE_TIME_TICKS / 20 + "с на відстані до " + RANGE + " блоків, то можна виявити небезпечні предмети в його інвентарі.";
    }

    @Override
    public int getSpiritualityCost() {
        return 0; // пасивна здібність
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        UUID casterId = caster.getUniqueId();

        if (observeTasks.containsKey(casterId)) {
            // Вимкнути здібність
            disableAbility(caster);
            caster.sendMessage(ChatColor.YELLOW + "Відчуття небезпеки" + ChatColor.GRAY + " вимкнене.");
        } else {
            // Увімкнути здібність
            enableAbility(caster);
            caster.sendMessage(ChatColor.GREEN + "Відчуття небезпеки" + ChatColor.GRAY + " увімкнене. Підійдіть до гравця на " + RANGE + " блоків.");
        }

        return true;
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Час спостереження", OBSERVE_TIME_TICKS / 20 + "с");
        attributes.put("Дальність", RANGE + " блоків");
        attributes.put("Кулдаун на ціль", COOLDOWN_PER_TARGET / 60 + "хв " + COOLDOWN_PER_TARGET % 60 + "с");

        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return 0; // пасивна здібність без загального кулдауну
    }

    private void enableAbility(Player caster) {
        UUID casterId = caster.getUniqueId();

        currentTargets.put(casterId, new ConcurrentHashMap<>());
        if (!targetCooldowns.containsKey(casterId)) {
            targetCooldowns.put(casterId, new ConcurrentHashMap<>());
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            scanForTargets(caster);
        }, 0L, CHECK_INTERVAL);

        observeTasks.put(casterId, task);
    }

    private void disableAbility(Player caster) {
        UUID casterId = caster.getUniqueId();

        BukkitTask task = observeTasks.remove(casterId);
        if (task != null) {
            task.cancel();
        }

        currentTargets.remove(casterId);
    }

    private void scanForTargets(Player caster) {
        if (!caster.isOnline()) {
            disableAbility(caster);
            return;
        }

        UUID casterId = caster.getUniqueId();
        Map<UUID, Integer> targets = currentTargets.get(casterId);
        Map<UUID, Long> cooldowns = targetCooldowns.get(casterId);

        if (targets == null || cooldowns == null) return;

        // Отримуємо всіх гравців поблизу
        Set<UUID> nearbyPlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(caster)) continue;
            if (player.getWorld().equals(caster.getWorld()) &&
                    player.getLocation().distance(caster.getLocation()) <= RANGE) {
                nearbyPlayers.add(player.getUniqueId());
            }
        }

        // Видаляємо цілі, які більше не поблизу
        targets.entrySet().removeIf(entry -> !nearbyPlayers.contains(entry.getKey()));

        // Обробляємо поточні цілі поблизу
        for (UUID targetId : nearbyPlayers) {
            // Перевіряємо кулдаун
            Long cooldownEnd = cooldowns.get(targetId);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                continue; // ця ціль на кулдауні
            }

            // Додаємо або оновлюємо час спостереження
            int currentTicks = targets.getOrDefault(targetId, 0);
            currentTicks += CHECK_INTERVAL;
            targets.put(targetId, currentTicks);

            // Перевіряємо, чи достатньо часу пройшло
            if (currentTicks >= OBSERVE_TIME_TICKS) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    analyzeDangerousItems(caster, target);

                    // Встановлюємо кулдаун на цю ціль
                    cooldowns.put(targetId, System.currentTimeMillis() + (COOLDOWN_PER_TARGET * 1000L));

                    // Видаляємо ціль з поточного спостереження
                    targets.remove(targetId);
                }
            }
        }

        // Очищуємо застарілі кулдауни
        long currentTime = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    private void analyzeDangerousItems(Player caster, Player target) {
        PlayerInventory inventory = target.getInventory();
        List<String> dangerousItems = new ArrayList<>();
        Map<String, Integer> itemCounts = new HashMap<>();

        // Перевіряємо основний інвентар
        for (ItemStack item : inventory.getContents()) {
            if (item != null && DANGEROUS_ITEMS.contains(item.getType())) {
                String itemName = getItemDisplayName(item);
                itemCounts.put(itemName, itemCounts.getOrDefault(itemName, 0) + item.getAmount());
            }
        }

        // Перевіряємо броню (може бути зачарована на шкоду)
        for (ItemStack armor : inventory.getArmorContents()) {
            if (armor != null && DANGEROUS_ITEMS.contains(armor.getType())) {
                String itemName = getItemDisplayName(armor);
                itemCounts.put(itemName, itemCounts.getOrDefault(itemName, 0) + armor.getAmount());
            }
        }

        // Формуємо список для виводу
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if (entry.getValue() > 1) {
                dangerousItems.add(entry.getKey() + " x" + entry.getValue());
            } else {
                dangerousItems.add(entry.getKey());
            }
        }

        // Виводимо результат
        if (dangerousItems.isEmpty()) {
            caster.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + target.getName() +
                    ChatColor.GRAY + " не має небезпечних предметів.");
        } else {
            caster.sendMessage(ChatColor.RED + "⚠ " + ChatColor.WHITE + target.getName() +
                    ChatColor.GRAY + " має небезпечні предмети:");
            for (String item : dangerousItems) {
                caster.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + item);
            }
        }
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Переклад назв предметів на українську
        switch (item.getType()) {
            case WOODEN_SWORD: return "Дерев'яний меч";
            case STONE_SWORD: return "Кам'яний меч";
            case IRON_SWORD: return "Залізний меч";
            case GOLDEN_SWORD: return "Золотий меч";
            case DIAMOND_SWORD: return "Алмазний меч";
            case NETHERITE_SWORD: return "Незеритовий меч";
            case BOW: return "Лук";
            case CROSSBOW: return "Арбалет";
            case TNT: return "Тротил";
            case TNT_MINECART: return "Вагонетка з тротилом";
            case SPLASH_POTION: return "Зілля-вибухівка";
            case LINGERING_POTION: return "Зависле зілля";
            case TRIDENT: return "Тризуб";
            case LAVA_BUCKET: return "Відро лави";
            case FIRE_CHARGE: return "Вогняний заряд";
            case FLINT_AND_STEEL: return "Кремінь і сталь";
            default:
                String name = item.getType().name().toLowerCase().replace('_', ' ');
                return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    // Очищення даних при відключенні гравця
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        BukkitTask task = observeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        currentTargets.remove(playerId);
        targetCooldowns.remove(playerId);

        // Також видаляємо цього гравця як ціль з усіх інших спостережень
        for (Map<UUID, Integer> targets : currentTargets.values()) {
            targets.remove(playerId);
        }

        for (Map<UUID, Long> cooldowns : targetCooldowns.values()) {
            cooldowns.remove(playerId);
        }
    }
}