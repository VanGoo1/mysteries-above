package me.vangoo.application.services.context;

import me.vangoo.application.services.AbilityLockManager;
import me.vangoo.application.services.CooldownManager;
import me.vangoo.domain.abilities.context.ICooldownContext;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Beyonder;

import java.util.UUID;

public class CooldownContext implements ICooldownContext {
    private final CooldownManager cooldownManager;
    private final AbilityLockManager lockManager;

    public CooldownContext(CooldownManager cooldownManager, AbilityLockManager lockManager) {
        this.cooldownManager = cooldownManager;
        this.lockManager = lockManager;
    }

    @Override
    public boolean hasCooldown(Beyonder beyonder, Ability ability) {
        return cooldownManager.isOnCooldown(beyonder, ability);
    }

    @Override
    public void setCooldown(Ability ability, UUID casterId) {
        cooldownManager.setCooldown(casterId, ability);
    }

    @Override
    public void clearCooldown(Ability ability, UUID casterId) {
        cooldownManager.clearCooldown(casterId, ability);
    }

    @Override
    public long getRemainingCooldownSeconds(Beyonder beyonder, Ability ability) {
        return cooldownManager.getRemainingCooldown(beyonder, ability);
    }

    @Override
    public void lockAbilities(UUID playerId, int durationSeconds) {
        lockManager.lockPlayer(playerId, durationSeconds);
    }
}
