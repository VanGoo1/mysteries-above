package me.vangoo.presentation.listeners;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PotionCauldronListener implements Listener {

    private final Plugin plugin;

    // Tracking already activated cauldrons to prevent spam
    private final Set<BlockLocation> activatedCauldrons = new HashSet<>();

    // Configuration
    private static final int PARTICLE_AMOUNT = 15;
    private static final double PARTICLE_OFFSET_X = 0.3;
    private static final double PARTICLE_OFFSET_Y = 0.5;
    private static final double PARTICLE_OFFSET_Z = 0.3;
    private static final int EFFECT_DURATION_TICKS = 60; // 3 seconds
    private static final int EFFECT_INTERVAL_TICKS = 5; // Every 0.25 seconds

    public PotionCauldronListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        Player player = event.getPlayer();

        // Case 1: Water cauldron placed above campfire
        if (isWaterCauldron(placedBlock)) {
            checkAndActivateCauldron(placedBlock, player);
            return;
        }

        // Case 3: Soul campfire placed under water cauldron
        if (placedBlock.getType() == Material.SOUL_CAMPFIRE) {
            Block blockAbove = placedBlock.getRelative(0, 1, 0);
            if (isWaterCauldron(blockAbove)) {
                checkAndActivateCauldron(blockAbove, player);
            }
        }
    }

    @EventHandler
    public void onWaterPour(PlayerBucketEmptyEvent event) {
        // Check if water is being poured
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }

        Block clickedBlock = event.getBlock();

        // Water can be poured into cauldron by clicking on it
        // or by clicking on adjacent block (water flows into cauldron)
        Block cauldron = null;

        // Check clicked block
        if (clickedBlock.getType() == Material.CAULDRON) {
            cauldron = clickedBlock;
        }
        // Check if there's a cauldron nearby
        else {
            // Water will be placed at blockClicked + blockFace direction
            Block waterBlock = clickedBlock.getRelative(event.getBlockFace());

            // Check if cauldron is at water location
            if (waterBlock.getType() == Material.CAULDRON) {
                cauldron = waterBlock;
            }
        }

        if (cauldron == null) {
            return;
        }

        Block finalCauldron = cauldron;
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if cauldron now has water
                if (isWaterCauldron(finalCauldron)) {
                    checkAndActivateCauldron(finalCauldron, event.getPlayer());
                }
            }
        }.runTaskLater(plugin, 1L);
    }


    private void checkAndActivateCauldron(Block cauldron, Player player) {
        // Must be water cauldron
        if (!isWaterCauldron(cauldron)) {
            return;
        }

        // Must have lit soul campfire below
        Block blockBelow = cauldron.getRelative(0, -1, 0);
        if (!isCampfireLit(blockBelow)) {
            return;
        }

        // Check if already activated (prevent spam)
        BlockLocation location = new BlockLocation(cauldron);
        if (activatedCauldrons.contains(location)) {
            return;
        }

        // Mark as activated
        activatedCauldrons.add(location);

        // Show activation effects
        playActivationEffects(cauldron, player);

        // Remove from set after effects end to allow re-activation if setup broken and restored
        new BukkitRunnable() {
            @Override
            public void run() {
                activatedCauldrons.remove(location);
            }
        }.runTaskLater(plugin, EFFECT_DURATION_TICKS);
    }

    /**
     * Check if block is water cauldron (with water)
     */
    private boolean isWaterCauldron(Block block) {
        if (block.getType() != Material.WATER_CAULDRON) {
            return false;
        }

        // Check water level
        Levelled cauldronData = (Levelled) block.getBlockData();
        return cauldronData.getLevel() > 0; // Has water
    }

    /**
     * Check if block is lit soul campfire
     */
    private boolean isCampfireLit(Block campfire) {
        if (campfire.getType() != Material.SOUL_CAMPFIRE) {
            return false;
        }

        org.bukkit.block.data.type.Campfire campfireData =
                (org.bukkit.block.data.type.Campfire) campfire.getBlockData();

        return campfireData.isLit();
    }

    /**
     * Show magical activation effects
     */
    private void playActivationEffects(Block cauldron, Player player) {
        // Sound effects
        cauldron.getWorld().playSound(
                cauldron.getLocation().add(0.5, 0.5, 0.5),
                Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                1.0f,
                1.5f
        );

        cauldron.getWorld().playSound(
                cauldron.getLocation().add(0.5, 0.5, 0.5),
                Sound.BLOCK_BEACON_ACTIVATE,
                0.7f,
                2.0f
        );

        // Message to player
        player.sendMessage("§d✦ §5Алхімічний верстак активовано!");

        // Start continuous particle effects
        startContinuousEffects(cauldron);
    }

    /**
     * Start continuous magical effects around cauldron
     */
    private void startContinuousEffects(Block cauldron) {
        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                // Stop after duration
                if (ticksElapsed >= EFFECT_DURATION_TICKS) {
                    this.cancel();
                    return;
                }

                // Check if cauldron still valid
                if (!isWaterCauldron(cauldron)) {
                    this.cancel();
                    return;
                }

                // Check if campfire still lit below
                Block blockBelow = cauldron.getRelative(0, -1, 0);
                if (!isCampfireLit(blockBelow)) {
                    this.cancel();
                    return;
                }

                // Show particles
                showMagicalParticles(cauldron);

                ticksElapsed += EFFECT_INTERVAL_TICKS;
            }
        }.runTaskTimer(plugin, 0L, EFFECT_INTERVAL_TICKS);
    }

    /**
     * Show magical particles around cauldron
     */
    private void showMagicalParticles(Block cauldron) {
        org.bukkit.Location centerLoc = cauldron.getLocation().add(0.5, 0.8, 0.5);

        // Purple soul flames (main effect)
        cauldron.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                centerLoc,
                PARTICLE_AMOUNT,
                PARTICLE_OFFSET_X,
                PARTICLE_OFFSET_Y,
                PARTICLE_OFFSET_Z,
                0.02
        );

        // Magical sparks
        cauldron.getWorld().spawnParticle(
                Particle.ENCHANT,
                centerLoc,
                8,
                PARTICLE_OFFSET_X,
                PARTICLE_OFFSET_Y,
                PARTICLE_OFFSET_Z,
                0.5
        );

        // Rare sparkles
        if (Math.random() < 0.3) {
            cauldron.getWorld().spawnParticle(
                    Particle.END_ROD,
                    centerLoc,
                    3,
                    PARTICLE_OFFSET_X * 0.5,
                    PARTICLE_OFFSET_Y * 0.5,
                    PARTICLE_OFFSET_Z * 0.5,
                    0.01
            );
        }

        // Occasional sound
        if (Math.random() < 0.1) {
            cauldron.getWorld().playSound(
                    centerLoc,
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    0.3f,
                    1.8f
            );
        }
    }

    private static class BlockLocation {
        private final UUID worldId;
        private final int x, y, z;

        public BlockLocation(Block block) {
            this.worldId = block.getWorld().getUID();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockLocation that)) return false;
            return x == that.x && y == that.y && z == that.z && worldId.equals(that.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}