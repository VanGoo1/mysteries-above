package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Жива сесія «Денне світло» — тримає єдиний осяйний блок, що йде за власником.
 * Тікає власним Bukkit-таском кожні {@link #RELOCATE_PERIOD_TICKS}: пересуває блок і
 * раз на {@link #DRAIN_EVERY_N_TICKS} циклів списує духовність. Вичерпано (або власник
 * офлайн) — сесія гасить сама себе.
 */
final class DaytimeSession {

    static final long RELOCATE_PERIOD_TICKS = 10L; // раз на пів секунди — встигає за рухом
    private static final int DRAIN_EVERY_N_TICKS = 10; // 10 * 10 тіків = 100 тіків (5 с)

    private final UUID ownerId;
    private final int periodicCost;
    private final IBeyonderContext beyonderContext;
    private final Map<UUID, DaytimeSession> sessions;
    private Location litBlock;
    private int tickCounter = 0;
    private BukkitTask task;

    DaytimeSession(UUID ownerId, int periodicCost, IBeyonderContext beyonderContext,
                   Map<UUID, DaytimeSession> sessions) {
        this.ownerId = ownerId;
        this.periodicCost = periodicCost;
        this.beyonderContext = beyonderContext;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    void applyNow() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        relocate(owner.getLocation());
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            cancel();
            return;
        }

        tickCounter++;
        if (tickCounter % DRAIN_EVERY_N_TICKS == 0) {
            Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
            if (beyonder == null || beyonder.getSpirituality().current() < periodicCost) {
                owner.sendActionBar(Component.text("✗ Духовність вичерпана — денне світло згасає"));
                cancel();
                return;
            }
            beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));
        }

        relocate(owner.getLocation());
    }

    /** Гасить світло, прибирає з активних, зупиняє таск. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);
        clearLight();
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0f, 0.5f);
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void relocate(Location casterLoc) {
        Location blockLoc = casterLoc.getBlock().getLocation();
        if (litBlock != null && litBlock.equals(blockLoc)) return;

        clearLight();

        Block target = blockLoc.getBlock();
        if (target.getType() != Material.AIR) return;

        target.setType(Material.LIGHT);
        if (target.getBlockData() instanceof Levelled levelled) {
            levelled.setLevel(levelled.getMaximumLevel());
            target.setBlockData(levelled);
        }
        litBlock = blockLoc;
    }

    private void clearLight() {
        if (litBlock == null) return;
        Block block = litBlock.getBlock();
        if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR);
        }
        litBlock = null;
    }
}
