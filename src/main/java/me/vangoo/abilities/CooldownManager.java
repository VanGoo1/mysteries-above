package me.vangoo.abilities;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<UUID, Map<Ability, Long>> playerCooldowns;

    public CooldownManager() {
        this.playerCooldowns = new ConcurrentHashMap<>();
    }

    public void setCooldown(UUID playerId, Ability ability) {
        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(ability, System.currentTimeMillis());
    }

    public boolean isOnCooldown(UUID playerId, Ability ability) {
        Map<Ability, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return false;
        Long lastUse = cooldowns.get(ability);
        if (lastUse == null) return false;

        return System.currentTimeMillis() - lastUse < ability.getCooldown() * 1000L;
    }

    public int getRemainingCooldown(UUID playerId, Ability ability) {
        Map<Ability, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return 0;
        Long lastUse = cooldowns.get(ability);
        if (lastUse == null) return 0;

        long timePassed = System.currentTimeMillis() - lastUse;
        long cooldownDuration = ability.getCooldown() * 1000L;
        if (timePassed >= cooldownDuration) {
            return 0;
        }

        long remaining = cooldownDuration - timePassed;
        return (int) Math.ceil(remaining / 1000.0);
    }
}
