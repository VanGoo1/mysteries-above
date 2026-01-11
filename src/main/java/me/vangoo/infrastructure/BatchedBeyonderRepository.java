package me.vangoo.infrastructure;

import me.vangoo.domain.entities.Beyonder;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Batched repository that saves changes periodically instead of on every update.
 * This prevents excessive disk I/O when many players are online.
 */
public class BatchedBeyonderRepository implements IBeyonderRepository {

    private final IBeyonderRepository delegate;
    private final Set<UUID> dirtyPlayers;
    private final AtomicBoolean saveInProgress;
    private final int batchIntervalTicks; // How often to save (in ticks)
    private volatile boolean autoSaveEnabled;

    public BatchedBeyonderRepository(IBeyonderRepository delegate, int batchIntervalTicks) {
        this.delegate = delegate;
        this.batchIntervalTicks = batchIntervalTicks;
        this.dirtyPlayers = ConcurrentHashMap.newKeySet();
        this.saveInProgress = new AtomicBoolean(false);
        this.autoSaveEnabled = true;
    }

    /**
     * Mark a player as having unsaved changes
     */
    private void markDirty(UUID playerId) {
        if (playerId != null) {
            dirtyPlayers.add(playerId);
        }
    }

    /**
     * Save all dirty players to disk
     */
    public void saveDirtyPlayers() {
        if (!autoSaveEnabled || saveInProgress.get()) {
            return;
        }

        if (dirtyPlayers.isEmpty()) {
            return;
        }

        saveInProgress.set(true);
        try {
            // Copy dirty players to avoid concurrent modification
            Set<UUID> playersToSave = new HashSet<>(dirtyPlayers);

            // Clear dirty set before saving to avoid losing new changes
            dirtyPlayers.removeAll(playersToSave);

            // Save all dirty players
            delegate.saveAll();

        } finally {
            saveInProgress.set(false);
        }
    }

    /**
     * Force immediate save of all data
     */
    public void forceSaveAll() {
        delegate.saveAll();
        dirtyPlayers.clear();
    }

    /**
     * Enable or disable auto-saving
     */
    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
        if (!enabled) {
            // If disabling auto-save, save immediately
            forceSaveAll();
        }
    }

    // Delegate all IBeyonderRepository methods

    @Override
    public boolean add(Beyonder beyonder) {
        boolean result = delegate.add(beyonder);
        if (result) {
            markDirty(beyonder.getPlayerId());
        }
        return result;
    }

    @Override
    public boolean remove(UUID playerId) {
        boolean result = delegate.remove(playerId);
        if (result) {
            dirtyPlayers.remove(playerId);
        }
        return result;
    }

    @Override
    public void saveAll() {
        forceSaveAll();
    }

    @Override
    public Beyonder get(UUID playerId) {
        return delegate.get(playerId);
    }

    @Override
    public boolean update(UUID playerId, Beyonder beyonder) {
        boolean result = delegate.update(playerId, beyonder);
        if (result) {
            markDirty(playerId);
        }
        return result;
    }

    @Override
    public java.util.Map<UUID, Beyonder> getAll() {
        return delegate.getAll();
    }

    /**
     * Get the number of dirty (unsaved) players
     */
    public int getDirtyCount() {
        return dirtyPlayers.size();
    }

    /**
     * Check if save is currently in progress
     */
    public boolean isSaveInProgress() {
        return saveInProgress.get();
    }
}