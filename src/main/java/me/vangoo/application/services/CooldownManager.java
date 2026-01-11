package me.vangoo.application.services;


import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<UUID, Map<AbilityIdentity, Long>> playerCooldowns;

    public CooldownManager() {
        this.playerCooldowns = new ConcurrentHashMap<>();
    }

    public void setCooldown(UUID playerId, Ability ability) {
        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(ability.getIdentity(), System.currentTimeMillis());
    }

    public boolean isOnCooldown(Beyonder beyonder, Ability ability) {
        Map<AbilityIdentity, Long> cooldowns = playerCooldowns.get(beyonder.getPlayerId());
        if (cooldowns == null) return false;
        Long lastUse = cooldowns.get(ability.getIdentity());
        if (lastUse == null) return false;

        return System.currentTimeMillis() - lastUse < ability.getCooldown(beyonder.getSequence()) * 1000L;
    }

    public int getRemainingCooldown(Beyonder beyonder, Ability ability) {
        Map<AbilityIdentity, Long> cooldowns = playerCooldowns.get(beyonder.getPlayerId());
        if (cooldowns == null) return 0;
        Long lastUse = cooldowns.get(ability.getIdentity());
        if (lastUse == null) return 0;

        long timePassed = System.currentTimeMillis() - lastUse;
        long cooldownDuration = ability.getCooldown(beyonder.getSequence()) * 1000L;
        if (timePassed >= cooldownDuration) {
            return 0;
        }

        long remaining = cooldownDuration - timePassed;
        return (int) Math.ceil(remaining / 1000.0);
    }

    public void clearCooldown(UUID playerId, Ability ability) {
        playerCooldowns.computeIfPresent(playerId, (id, cooldowns) -> {
            cooldowns.remove(ability.getIdentity());
            return cooldowns.isEmpty() ? null : cooldowns;
        });
    }
}
