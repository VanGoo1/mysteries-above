package me.vangoo.presentation.listeners;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.infrastructure.forage.ForageNode;
import me.vangoo.infrastructure.schedulers.ForageNodeSpawner;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Збір фореджу: гравець ламає «зачарований» блок-донор -> ванільний дроп скасовується,
 * падає лише допоміжний інгредієнт; нода знімається з реєстру та PDC чанка.
 */
public class ForageHarvestListener implements Listener {

    private final ForageNodeSpawner spawner;
    private final CustomItemService customItemService;
    private final Plugin plugin;

    public ForageHarvestListener(ForageNodeSpawner spawner, CustomItemService customItemService, Plugin plugin) {
        this.spawner = spawner;
        this.customItemService = customItemService;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Optional<ForageNode> node = spawner.nodeAt(event.getBlock());
        if (node.isEmpty()) return;
        event.setDropItems(false); // ванільний дроп донора скасовано — падає лише інгредієнт
        harvest(node.get(), event.getBlock());
    }

    private void harvest(ForageNode node, Block block) {
        spawner.onHarvested(node);
        Optional<ItemStack> stack = customItemService.createItemStack(node.getIngredientId());
        if (stack.isEmpty()) {
            plugin.getLogger().warning("Forage harvest: could not resolve ingredient item for "
                    + node.getIngredientId());
            return;
        }
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.3, 0.5);
        world.dropItem(loc, stack.get());
        world.playSound(loc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.2f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0.0);
    }
}
