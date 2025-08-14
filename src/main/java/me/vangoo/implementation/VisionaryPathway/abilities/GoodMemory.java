package me.vangoo.implementation.VisionaryPathway.abilities;

import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GoodMemory extends Ability {
    private static final int OBSERVE_TICKS_REQUIRED = 5 * 20; // 5s
    private static final int GLOW_TICKS = 20 * 20; // 20s
    private static final int RANGE = 30;

    // Для кожного гравця, який вмикнув здатність, тримаємо таск і стан спостереження
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, ObservingState> states = new ConcurrentHashMap<>();

    // Для відстеження підсвічених entities
    private final Map<UUID, Set<UUID>> glowingEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, BukkitTask>> glowTasks = new ConcurrentHashMap<>();

    private static class ObservingState {
        LivingEntity current;
        int ticks;
    }

    @Override
    public String getName() {
        return "[Пасивна] Хороша пам'ять";
    }

    @Override
    public String getDescription() {
        return "Після " + OBSERVE_TICKS_REQUIRED / 20 + "с спостереження за мобом чи гравцем — ціль підсвічується на " + GLOW_TICKS / 20 + "с.";
    }

    @Override
    public int getSpiritualityCost() {
        return 0; // пасивна, без вартості
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        UUID id = caster.getUniqueId();
        if (tasks.containsKey(id)) {
            // Вимкнути
            tasks.remove(id).cancel();
            states.remove(id);

            // Видалити всі підсвічування для цього гравця
            removeAllGlowing(caster);

            caster.sendMessage(ChatColor.YELLOW + "Хороша пам'ять" + ChatColor.GRAY + " вимкнена.");
        } else {
            // Увімкнути
            ObservingState state = new ObservingState();
            states.put(id, state);
            glowingEntities.put(id, ConcurrentHashMap.newKeySet());
            glowTasks.put(id, new ConcurrentHashMap<>());

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickObserver(caster, state), 1L, 5L);
            tasks.put(id, task);
            caster.sendMessage(ChatColor.GREEN + "Хороша пам'ять" + ChatColor.GRAY + " увімкнена. Спостерігайте за ціллю 5с.");
        }
        return true; // перемикач завжди "успішний"
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Тривалість", GLOW_TICKS / 20 + "с");
        attributes.put("Дальність", RANGE + " блоків");

        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return 0; // пасивна
    }

    private void tickObserver(Player caster, ObservingState state) {
        if (caster == null || !caster.isOnline()) return;

        LivingEntity target = getLookTarget(caster, RANGE);
        if (target == null || !caster.hasLineOfSight(target)) {
            // Немає валідної цілі — скинути стан
            state.current = null;
            state.ticks = 0;
            return;
        }

        if (state.current == null || !state.current.equals(target)) {
            state.current = target;
            state.ticks = 0;
        } else {
            state.ticks += 5; // крок тика — 5 тік
        }

        if (state.ticks >= OBSERVE_TICKS_REQUIRED) {
            UUID casterId = caster.getUniqueId();
            UUID targetId = target.getUniqueId();

            // Перевірити чи ціль вже підсвічена для цього гравця
            if (!glowingEntities.get(casterId).contains(targetId)) {
                addGlowing(caster, target);
                caster.sendMessage(ChatColor.GREEN + "Ви запам'ятали " + ChatColor.WHITE + getEntityName(target)
                        + ChatColor.GREEN + ". Його підсвічено на 20с.");
            }
            // Після спрацьовування почати відлік знову (щоб можна було підсвітити іншу ціль)
            state.ticks = 0;
        }
    }

    private void addGlowing(Player caster, LivingEntity target) {
        UUID casterId = caster.getUniqueId();
        UUID targetId = target.getUniqueId();

        // Додати до списку підсвічених
        glowingEntities.get(casterId).add(targetId);

        // Включити підсвічування через GlowingEntities API з жовтим кольором
        try {
            plugin.getGlowingEntities().setGlowing(target, caster, ChatColor.YELLOW);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to set glowing for entity " + target.getUniqueId() + ": " + e.getMessage());

            glowingEntities.get(casterId).remove(targetId);
            return;
        }

        // Створити таск для видалення підсвічування через GLOW_TICKS
        BukkitTask glowTask = Bukkit.getScheduler().runTaskLater(plugin, () -> removeGlowing(caster, target), GLOW_TICKS);

        glowTasks.get(casterId).put(targetId, glowTask);
    }

    private void removeGlowing(Player caster, LivingEntity target) {
        if (caster == null || !caster.isOnline()) return;

        UUID casterId = caster.getUniqueId();
        UUID targetId = target.getUniqueId();

        Set<UUID> playerGlowing = glowingEntities.get(casterId);
        if (playerGlowing != null && playerGlowing.contains(targetId)) {
            playerGlowing.remove(targetId);

            // Видалити підсвічування через GlowingEntities API
            if (target.isValid() && !target.isDead()) {
                try {
                    plugin.getGlowingEntities().unsetGlowing(target, caster);
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().warning("Failed to unset glowing for entity " + target.getUniqueId() + ": " + e.getMessage());
                }
            }

            // Скасувати таск, якщо він існує
            Map<UUID, BukkitTask> playerTasks = glowTasks.get(casterId);
            if (playerTasks != null) {
                BukkitTask task = playerTasks.remove(targetId);
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }

    private void removeAllGlowing(Player caster) {
        UUID casterId = caster.getUniqueId();

        Set<UUID> playerGlowing = glowingEntities.get(casterId);
        if (playerGlowing != null) {
            // Видалити всі підсвічування
            for (UUID targetId : playerGlowing) {
                Entity target = Bukkit.getEntity(targetId);
                if (target instanceof LivingEntity && target.isValid() && !target.isDead()) {
                    try {
                        plugin.getGlowingEntities().unsetGlowing(target, caster);
                    } catch (ReflectiveOperationException e) {
                        plugin.getLogger().warning("Failed to unset glowing for entity " + target.getUniqueId() + ": " + e.getMessage());
                    }
                }
            }
            playerGlowing.clear();
        }

        // Скасувати всі таски підсвічування
        Map<UUID, BukkitTask> playerTasks = glowTasks.get(casterId);
        if (playerTasks != null) {
            for (BukkitTask task : playerTasks.values()) {
                task.cancel();
            }
            playerTasks.clear();
        }

        glowingEntities.remove(casterId);
        glowTasks.remove(casterId);
    }

    private String getEntityName(Entity e) {
        if (e instanceof Player) return e.getName();
        if (e.getCustomName() != null) return e.getCustomName();
        return e.getType().name();
    }

    private LivingEntity getLookTarget(Player player, double range) {
        RayTraceResult res = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                range,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );
        if (res != null && res.getHitEntity() instanceof LivingEntity) {
            return (LivingEntity) res.getHitEntity();
        }
        return null;
    }
}