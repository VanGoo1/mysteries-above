package me.vangoo.domain.abilities.context;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Beyonder;

import java.util.UUID;

public interface ICooldownContext {
    boolean hasCooldown(Beyonder beyonder, Ability ability);

    void setCooldown(Ability ability, UUID casterId);

    void clearCooldown(Ability ability,UUID casterId);

    long getRemainingCooldownSeconds(Beyonder beyonder, Ability ability);

    void lockAbilities(UUID playerId, int durationSeconds);
}
