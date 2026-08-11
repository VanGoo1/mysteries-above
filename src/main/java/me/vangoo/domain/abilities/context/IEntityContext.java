package me.vangoo.domain.abilities.context;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.UUID;


public interface IEntityContext {

    /**
     * Спавнить істоту з MythicMobs-пака за її internal name (напр. {@code spirit_wandering}).
     *
     * <p>Єдиний шлях, яким шар здібностей може покликати істоту: пряме звернення до MythicMobs
     * API поза {@code infrastructure.mythic} заборонене
     * ({@code ArchitectureTest.mythicMobsApiIsConfinedToBridgePackage}).
     *
     * @return id створеної сутності або порожньо, якщо моба з таким іменем немає чи локація
     *         непридатна — викликач мусить це врахувати, а не вважати спавн гарантованим.
     */
    Optional<UUID> summonCreature(String mobId, Location location);

    void teleport(UUID entityId, Location location);

    void damage(UUID entityId, double amount);

    void heal(UUID entityId, double amount);

    void applyPotionEffect(UUID entityId, PotionEffectType effect, int durationTicks, int amplifier);

    void removePotionEffect(UUID entityId, PotionEffectType effect);

    void reducePotionEffectDuration(UUID entityId, PotionEffectType effect, int ticksToReduce);

    void removeAllPotionEffects(UUID entityId);

    void consumeItem(UUID humanEntityId, ItemStack item);

    void dropItem(UUID humanEntityId, ItemStack item);

    void giveItem(UUID humanEntityId, ItemStack item);

    void removeItem(UUID playerId, ItemStack item);

    void setHidden(UUID playerId, boolean hidden);

    void hidePlayerFromTarget(UUID playerId, UUID playerToHide);

    void showPlayerToTarget(UUID playerId, UUID playerToShowId);

    void setGameMode(UUID entityId, GameMode gameMode);

    void leaveVehicle(UUID targetId);

    void setVelocity(UUID targetId, Vector vector);

    void setSprinting(UUID targetId, boolean value);

    void giveExperience(UUID entityId, int amount);
}
