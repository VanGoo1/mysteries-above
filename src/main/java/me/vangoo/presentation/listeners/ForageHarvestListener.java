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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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

    // ---- Нештатне знищення ноди: повертаємо оригінал і ДАЄМО ванільній події знищити вже
    // оригінальний блок (без інгредієнта і без предмета-донора). Події свідомо НЕ скасовуємо:
    // інакше нода була б «незнищенною» (вода впиралась би в невидиму стіну), а без відновлення
    // вибух дропнув би предмет самого донора. ----

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        restoreAll(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        restoreAll(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        restoreAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        restoreAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        restoreOne(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        restoreOne(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        // Донор ставиться persistent=true; це страховка, якщо стан збили ззовні.
        spawner.nodeAt(event.getBlock()).ifPresent(n -> {
            event.setCancelled(true);
            spawner.restoreAndRemove(n);
        });
    }

    // ---- Життєвий цикл чанків: живі ноди існують лише в завантажених чанках ----

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (ForageNode n : spawner.nodesInChunk(event.getChunk())) {
            spawner.restoreAndRemove(n);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        spawner.healChunk(event.getChunk()); // «осиротілі» записи після крешу
    }

    private void restoreAll(List<Block> blocks) {
        for (Block b : blocks) restoreOne(b);
    }

    private void restoreOne(Block block) {
        spawner.nodeAt(block).ifPresent(spawner::restoreAndRemove);
    }
}
