package me.vangoo.presentation.listeners;

import me.vangoo.application.services.PotionCraftingService;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Presentation Listener: Handles potion crafting in cauldrons
 */
public class PotionCraftingListener implements Listener {
    private final Plugin plugin;
    private final PotionCraftingService craftingService;

    // Track items thrown into cauldrons
    private final Map<Location, CauldronCrafting> activeCauldrons = new HashMap<>();

    private static final int CRAFTING_TIMEOUT_TICKS = 100; // 5 seconds
    private static final double CAULDRON_RADIUS = 1.0;

    public PotionCraftingListener(Plugin plugin, PotionCraftingService craftingService) {
        this.plugin = plugin;
        this.craftingService = craftingService;
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();
        Location itemLocation = droppedItem.getLocation();

        // Find nearby activated cauldron
        Block cauldron = findNearbyActivatedCauldron(itemLocation);
        if (cauldron == null) {
            return;
        }

        Location cauldronLoc = cauldron.getLocation();

        // Get or create crafting session
        CauldronCrafting crafting = activeCauldrons.computeIfAbsent(
                cauldronLoc,
                loc -> new CauldronCrafting(player.getUniqueId())
        );

        // Add item to crafting
        crafting.addItem(droppedItem.getItemStack().clone());

        // Remove the dropped item
        droppedItem.remove();

        // Show particle effect
        showIngredientAddedEffect(cauldron);

        // Start or reset timeout
        startCraftingTimeout(cauldronLoc, player);
    }

    /**
     * Find activated cauldron near location
     */
    private Block findNearbyActivatedCauldron(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Check blocks in radius
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);

                    if (isActivatedCauldron(block)) {
                        return block;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if block is activated cauldron
     */
    private boolean isActivatedCauldron(Block block) {
        if (block.getType() != Material.WATER_CAULDRON) {
            return false;
        }

        // Must have water
        Levelled cauldronData = (Levelled) block.getBlockData();
        if (cauldronData.getLevel() == 0) {
            return false;
        }

        // Must have lit soul campfire below
        Block blockBelow = block.getRelative(0, -1, 0);
        if (blockBelow.getType() != Material.SOUL_CAMPFIRE) {
            return false;
        }

        org.bukkit.block.data.type.Campfire campfireData =
                (org.bukkit.block.data.type.Campfire) blockBelow.getBlockData();

        return campfireData.isLit();
    }

    /**
     * Start or reset crafting timeout
     */
    private void startCraftingTimeout(Location cauldronLoc, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                CauldronCrafting crafting = activeCauldrons.get(cauldronLoc);
                if (crafting == null) {
                    return;
                }

                // Try to craft
                attemptCrafting(cauldronLoc, crafting, player);
            }
        }.runTaskLater(plugin, CRAFTING_TIMEOUT_TICKS);
    }

    /**
     * Attempt to craft potion from collected ingredients
     */
    private void attemptCrafting(Location cauldronLoc, CauldronCrafting crafting, Player player) {
        Block cauldron = cauldronLoc.getBlock();

        // Check if cauldron still valid
        if (!isActivatedCauldron(cauldron)) {
            failCrafting(cauldronLoc, crafting, "Котел більше не активний!");
            return;
        }

        // Try to craft
        PotionCraftingService.CraftingResult result = craftingService.trycraft(
                crafting.playerId,
                crafting.items
        );

        if (result.success()) {
            successCrafting(cauldronLoc, crafting, result, player);
        } else {
            failCrafting(cauldronLoc, crafting, result.message());
        }
    }

    /**
     * Handle successful crafting
     */
    private void successCrafting(
            Location cauldronLoc,
            CauldronCrafting crafting,
            PotionCraftingService.CraftingResult result,
            Player player) {

        Block cauldron = cauldronLoc.getBlock();

        // Spawn potion
        Location spawnLoc = cauldronLoc.clone().add(0.5, 1.5, 0.5);
        cauldron.getWorld().dropItem(spawnLoc, result.potion());

        // Success effects
        cauldron.getWorld().spawnParticle(
                Particle.WITCH,
                spawnLoc,
                50,
                0.3, 0.3, 0.3,
                0.1
        );

        cauldron.getWorld().spawnParticle(
                Particle.END_ROD,
                spawnLoc,
                30,
                0.2, 0.2, 0.2,
                0.05
        );

        cauldron.getWorld().playSound(
                cauldronLoc,
                Sound.BLOCK_BREWING_STAND_BREW,
                1.0f,
                1.2f
        );

        cauldron.getWorld().playSound(
                cauldronLoc,
                Sound.ENTITY_PLAYER_LEVELUP,
                0.8f,
                1.5f
        );

        // Message to player
        player.sendMessage(ChatColor.GREEN + "✓ " + result.message());

        // Clear cauldron water (consumed in crafting)
        cauldron.setType(Material.CAULDRON);

        // Remove from tracking
        activeCauldrons.remove(cauldronLoc);
    }

    /**
     * Handle failed crafting
     */
    private void failCrafting(Location cauldronLoc, CauldronCrafting crafting, String reason) {
        Block cauldron = cauldronLoc.getBlock();

        // Scatter ingredients
        Location center = cauldronLoc.clone().add(0.5, 1.2, 0.5);
        Random random = new Random();

        for (ItemStack item : crafting.items) {
            Vector velocity = new Vector(
                    random.nextDouble() * 0.4 - 0.2,
                    random.nextDouble() * 0.3 + 0.2,
                    random.nextDouble() * 0.4 - 0.2
            );

            Item droppedItem = cauldron.getWorld().dropItem(center, item);
            droppedItem.setVelocity(velocity);
        }

        // Failure effects
        cauldron.getWorld().spawnParticle(
                Particle.SMOKE,
                center,
                30,
                0.3, 0.3, 0.3,
                0.05
        );

        cauldron.getWorld().spawnParticle(
                Particle.LAVA,
                center,
                10,
                0.2, 0.2, 0.2,
                0.0
        );

        cauldron.getWorld().playSound(
                cauldronLoc,
                Sound.BLOCK_FIRE_EXTINGUISH,
                1.0f,
                0.8f
        );

        // Message to player
        Player player = Bukkit.getPlayer(crafting.playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.RED + "✗ Крафт провалився: " + reason);
        }

        // Remove from tracking
        activeCauldrons.remove(cauldronLoc);
    }

    /**
     * Show effect when ingredient added
     */
    private void showIngredientAddedEffect(Block cauldron) {
        Location loc = cauldron.getLocation().add(0.5, 1.0, 0.5);

        cauldron.getWorld().spawnParticle(
                Particle.SPLASH,
                loc,
                10,
                0.2, 0.1, 0.2,
                0.1
        );

        cauldron.getWorld().spawnParticle(
                Particle.ENCHANT,
                loc,
                5,
                0.2, 0.2, 0.2,
                0.5
        );

        cauldron.getWorld().playSound(
                loc,
                Sound.ENTITY_GENERIC_SPLASH,
                0.5f,
                1.2f
        );
    }

    /**
     * Internal class to track crafting session
     */
    private static class CauldronCrafting {
        private final UUID playerId;
        private final List<ItemStack> items;
        private final long startTime;

        public CauldronCrafting(UUID playerId) {
            this.playerId = playerId;
            this.items = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
        }

        public void addItem(ItemStack item) {
            items.add(item);
        }
    }
}