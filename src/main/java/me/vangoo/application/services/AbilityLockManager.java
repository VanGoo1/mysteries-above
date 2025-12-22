package me.vangoo.application.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityLockManager {
    private final Map<UUID, Long> lockedPlayers = new ConcurrentHashMap<>();

    public void lockPlayer(UUID uuid, int durationSeconds) {
        long expirationTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        lockedPlayers.put(uuid, expirationTime);
    }

    public boolean isLocked(UUID uuid) {
        Long expirationTime = lockedPlayers.get(uuid);

        if (expirationTime == null) {
            return false; // Гравець не заблокований
        }

        if (System.currentTimeMillis() > expirationTime) {
            lockedPlayers.remove(uuid); // Час блокування минув
            return false;
        }
        return true;
    }
}
