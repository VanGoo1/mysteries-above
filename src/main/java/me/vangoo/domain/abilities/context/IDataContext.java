package me.vangoo.domain.abilities.context;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IDataContext {
    Map<String, String> getTargetAnalysis(UUID targetId);

    List<String> getEnderChestContents(UUID playerId, int limit);

    int getPlayerKills(UUID target);

    boolean hasItem(UUID playerId, Material material, int amount);

    Location getBedSpawnLocation(UUID playerId);

    long getPlayTimeHours(UUID playerId);

    String getMainHandItemName(UUID playerId);

    ItemStack getMainHandItem(UUID playerId);

    int getDeathsCount(UUID playerId);

    int getVillagerKills(UUID playerId);

    Location getLastDeathLocation(UUID playerId);

    double getHealth(UUID playerId);

    int getFoodLevel(UUID playerId);

    float getSaturation(UUID playerId);

    double getMaxHealth(UUID playerId);

    int getExperienceLevel(UUID playerId);

    int getMinedAmount(UUID playerId, Material oreType);

    int getUsedAmount(UUID playerId, Material itemType);

    boolean isOnline(UUID playerId);

    Location getCurrentLocation(UUID entityId);

    List<ItemStack> getInventoryContents(UUID playerId);

    List<PotionEffect> getActivePotionEffects(UUID playerId);

    String getName(UUID targetId);

    boolean isSneaking(UUID targetId);

    Location getEyeLocation(UUID playerId);

    boolean isInsideVehicle(UUID targetId);

    Vector getVelocity(UUID targetId);
}
