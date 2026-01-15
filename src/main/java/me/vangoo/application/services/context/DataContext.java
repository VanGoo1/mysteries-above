package me.vangoo.application.services.context;

import me.vangoo.domain.abilities.context.IDataContext;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class DataContext implements IDataContext {

    public DataContext() {
    }

    @Override
    public Map<String, String> getTargetAnalysis(UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return Map.of("Error", "Not a player");
        }

        int kills = 0;
        try {
            kills = target.getStatistic(Statistic.PLAYER_KILLS);
        } catch (IllegalArgumentException ignored) {
        }

        int deaths = 0;
        try {
            deaths = target.getStatistic(Statistic.DEATHS);
        } catch (IllegalArgumentException ignored) {
        }

        int mobKills = 0;
        try {
            mobKills = target.getStatistic(Statistic.MOB_KILLS);
        } catch (IllegalArgumentException ignored) {
        }

        long hours = 0;
        try {
            hours = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60 / 60;
        } catch (IllegalArgumentException ignored) {
        }

        ItemStack handItem = target.getInventory().getItemInMainHand();
        String weaponName = "Нічого/Кулаки";
        if (handItem.getType() != Material.AIR) {
            if (handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()) {
                weaponName = handItem.getItemMeta().getDisplayName();
            } else {
                weaponName = handItem.getType().name().replace("_", " ").toLowerCase();
            }
        }

        Map<String, String> data = new HashMap<>();
        data.put("Kills", String.valueOf(kills));
        data.put("Deaths", String.valueOf(deaths));
        data.put("MobKills", String.valueOf(mobKills));
        data.put("Hours", String.valueOf(hours));
        data.put("Weapon", weaponName);

        return data;
    }

    @Override
    public List<String> getEnderChestContents(UUID playerId, int limit) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return Collections.emptyList();

        Map<String, Integer> mergedItems = new HashMap<>();

        for (ItemStack item : target.getEnderChest().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                String name = formatMaterialName(item.getType());
                mergedItems.put(name, mergedItems.getOrDefault(name, 0) + item.getAmount());
            }
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mergedItems.entrySet()) {
            result.add(ChatColor.AQUA + entry.getKey() + ChatColor.WHITE + " x" + entry.getValue());
        }

        if (limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    @Override
    public int getPlayerKills(UUID target) {
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) return 0;
        try {
            return targetPlayer.getStatistic(Statistic.PLAYER_KILLS);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override
    public boolean hasItem(UUID playerId, Material material, int amount) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return false;
        return target.getInventory().contains(material, amount);
    }

    @Override
    public Location getBedSpawnLocation(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        return target != null ? target.getRespawnLocation() : null;
    }

    @Override
    public long getPlayTimeHours(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return 0;
        try {
            return target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60 / 60;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override
    public String getMainHandItemName(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return "Невідомо";
        ItemStack item = target.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return "Нічого";

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    @Override
    public ItemStack getMainHandItem(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return new ItemStack(Material.AIR);
        }
        return player.getInventory().getItemInMainHand();
    }

    @Override
    public int getDeathsCount(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return 0;
        try {
            return target.getStatistic(Statistic.DEATHS);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override
    public int getVillagerKills(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return 0;
        try {
            return target.getStatistic(Statistic.KILL_ENTITY, EntityType.VILLAGER);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override
    public Location getLastDeathLocation(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        return target != null ? target.getLastDeathLocation() : null;
    }

    @Override
    public double getHealth(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);

        // Якщо гравець офлайн або не знайдений -> 0
        if (player == null) {
            return 0.0;
        }

        return player.getHealth();
    }

    @Override
    public int getFoodLevel(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null) {
            return 0;
        }
        return player.getFoodLevel();
    }

    @Override
    public float getSaturation(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null) {
            return 0;
        }
        return player.getSaturation();
    }

    @Override
    public double getMaxHealth(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null) {
            return 0.0;
        }

        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);

        if (attribute != null) {
            return attribute.getValue();
        }

        return 20.0;
    }

    @Override
    public int getExperienceLevel(UUID playerId) {
        Player target = Bukkit.getPlayer(playerId);
        return target != null ? target.getLevel() : 0;
    }

    @Override
    public int getMinedAmount(UUID playerId, Material oreType) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return 0;
        try {
            return target.getStatistic(Statistic.MINE_BLOCK, oreType);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override
    public int getUsedAmount(UUID playerId, Material itemType) {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null) return 0;

        int crafted = 0;
        int used = 0;

        try {
            crafted = target.getStatistic(Statistic.CRAFT_ITEM, itemType);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            used = target.getStatistic(Statistic.USE_ITEM, itemType);
        } catch (IllegalArgumentException ignored) {
        }

        return crafted + used;
    }

    @Override
    public boolean isOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    @Override
    public Location getCurrentLocation(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);

        if (entity == null) {
            return null;
        }

        return entity.getLocation();
    }

    @Override
    public List<ItemStack> getInventoryContents(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return Collections.emptyList();
        return List.of(player.getInventory().getContents());
    }

    @Override
    public List<PotionEffect> getActivePotionEffects(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null) {
            return List.of();
        }

        return new ArrayList<>(player.getActivePotionEffects());
    }


    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}